package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.GitHubException;
import com.workday.reactive.actor.messages.Listen;
import com.workday.reactive.actor.messages.Load;
import com.workday.reactive.actor.messages.Message;
import com.workday.reactive.configuration.GitHubConfig;
import com.workday.reactive.retry.Retryable;
import org.apache.commons.collections4.CollectionUtils;
import org.kohsuke.github.*;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.workday.reactive.Constants.REACTIVE;

/**
 * @author lmedina
 */
public class GitHubEventsListenerActor extends AbstractLoggingActor {
    private GitHubBuilder gitHubBuilder;
    private RateLimiter rateLimiter;
    private ActorRef manager;
    private Retryable retryable;
    private Long listeningIntervalSeconds;

    private Long latestEventMillis = Long.MIN_VALUE;

    private GHRepository latestRepo;

    public static Props props(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, ActorRef manager, Retryable retryable) {
        return Props.create(GitHubEventsListenerActor.class, gitHubBuilder, gitHubRateLimiter, manager, retryable);
    }

    GitHubEventsListenerActor(GitHubBuilder gitHubBuilder, RateLimiter rateLimiter, ActorRef manager, Retryable retryable) {
        this.gitHubBuilder = gitHubBuilder;
        this.rateLimiter = rateLimiter;
        this.manager = manager;
        this.retryable = retryable;

        listeningIntervalSeconds = context().system().settings().config().getLong(GitHubConfig.LISTENING_INTERVAL_SECONDS);

        self().tell(new Load(), self());
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(Load.class, msg -> loadGitHubRepositories())
                .match(Listen.class, msg -> listen())
                .build();
    }

    private void loadGitHubRepositories() throws GitHubException {
        try {
            log().info("Loading up initial GitHub repositories.");
            readGitHubRepositories();
            self().tell(new Listen(), self());
        } catch (Throwable e) {
            log().warning("There was an issue connecting to GitHub to load repositories. Retrying...");
            context().become(retrying);
            retry(new Load());
        }
    }

    private void readGitHubRepositories() throws Throwable {
        rateLimiter.acquire();
        PagedSearchIterable<GHRepository> searchIterable = gitHubBuilder.build().searchRepositories().q(REACTIVE).list();
        Iterator<GHRepository> iterator = searchIterable.iterator();

        while (iterator.hasNext()) {
            rateLimiter.acquire();
            GHRepository repository = iterator.next();
            manager.tell(repository, self());
        }
    }

    private void listen() throws GitHubException {
        try {
            readEvents();
            scheduleMessage(new Listen(), listeningIntervalSeconds, TimeUnit.SECONDS);
        } catch (Throwable e) {
            log().warning("There was an issue while listening to events from GitHub. Retrying...");
            retry(new Listen());
        }
    }

    private void readEvents() throws Throwable {
        rateLimiter.acquire();
        List<GHEventInfo> events = gitHubBuilder.build().getEvents();
        List<GHEventInfo> filteredEvents = getFilteredEvents(events);
        List<GHRepository> repositories = getRepositories(filteredEvents);

        repositories.forEach(repo -> manager.tell(repo, self()));

        if (!CollectionUtils.isEmpty(repositories)) {
            latestRepo = repositories.get(0);
            latestEventMillis = filteredEvents.get(0).getCreatedAt().getTime();
        }
    }

    private List<GHEventInfo> getFilteredEvents(List<GHEventInfo> events) {
        return events.stream().filter(event -> event.getType() == GHEvent.CREATE)
                              .filter(event -> event.getCreatedAt().getTime() > latestEventMillis)
                              .collect(Collectors.toList());
    }

    private List<GHRepository> getRepositories(List<GHEventInfo> events) {
        return events.stream().map(this::getRepository)
                              .filter(repo -> repo != null)
                              .filter(repo -> latestRepo == null || repo.getId() != latestRepo.getId())
                              .filter(repo -> repo.getFullName().toLowerCase().contains(REACTIVE))
                              .collect(Collectors.toList());
    }

    private GHRepository getRepository(GHEventInfo event) {
        try {
            rateLimiter.acquire();
            return event.getRepository();
        } catch (IOException e) {
            return null;
        }
    }

    private PartialFunction<Object, BoxedUnit> retrying = ReceiveBuilder
            .match(Load.class, msg -> retryLoadingGitHubRepositories())
            .match(Listen.class, msg -> retryListening())
            .build();

    private void retry(Message message) throws GitHubException {
        if (retryable.shouldRetry()) {
            long waitTime = retryable.incrementRetryCountAndGetWaitTime();
            scheduleMessage(message, waitTime, TimeUnit.MILLISECONDS);
        } else {
            throw new GitHubException();
        }
    }

    private void retryLoadingGitHubRepositories() throws GitHubException {
        try {
            readGitHubRepositories();
            context().unbecome();
            retryable.reset();
        } catch (Throwable e) {
            log().warning("There was an issue re-connecting to GitHub to load repositories. Retrying...");
            retry(new Load());
        }
    }

    private void retryListening() throws GitHubException {
        try {
            readEvents();
            context().unbecome();
            retryable.reset();
        } catch (Throwable e) {
            log().warning("There was an issue while re-connecting to GitHub to listen to events. Retrying...");
            retry(new Listen());
        }
    }

    private void scheduleMessage(Object obj, Long length, TimeUnit unit) {
        context().system().scheduler().scheduleOnce(
                Duration.create(length, unit),
                self(),
                obj,
                context().dispatcher(),
                null);
    }
}
