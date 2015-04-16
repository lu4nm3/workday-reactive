package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.GitHubException;
import com.workday.reactive.actor.messages.Initialize;
import com.workday.reactive.actor.messages.Start;
import org.apache.commons.collections4.CollectionUtils;
import org.kohsuke.github.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.workday.reactive.Constants.REACTIVE;

/**
 * @author lmedina
 */
public class GitHubEventsListenerActor extends AbstractLoggingActor {
    private GitHubBuilder gitHubBuilder;
    private GitHub gitHubListener;
    private RateLimiter gitHubRateLimiter;
    private ActorRef manager;

    private Long latestEventMillis;
    private GHRepository latestRepo;

    public static Props props(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, ActorRef manager) {
        return Props.create(GitHubEventsListenerActor.class, gitHubBuilder, gitHubRateLimiter, manager);
    }

    GitHubEventsListenerActor(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, ActorRef manager) {
        this.gitHubBuilder = gitHubBuilder;
        this.gitHubRateLimiter = gitHubRateLimiter;
        this.manager = manager;

        latestEventMillis = Long.MIN_VALUE;
        latestRepo = null;

        self().tell(new Start(), self());
    }

    @Override
    public void preStart() throws GitHubException {
//        self().tell(new Initialize(), self());
    }

    @Override
    public void postRestart(Throwable reason) {
        // Overriding postRestart to disable the call to preStart() after restarts in order to prevent.
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(Initialize.class, msg -> loadCurrentGitHubRepositories())
                .match(Start.class, msg -> listen())
                .build();
    }

    private void loadCurrentGitHubRepositories() throws GitHubException {
        try {
            PagedSearchIterable<GHRepository> searchIterable = gitHubBuilder.build().searchRepositories().q(REACTIVE).list();
            Iterator<GHRepository> iterator = searchIterable.iterator();

            while (iterator.hasNext()) {
                gitHubRateLimiter.acquire();
                GHRepository repository = iterator.next();
                manager.tell(repository, self());
            }
        } catch (IOException e) {
            throw new GitHubException(e);
        }
    }

    private void listen() throws GitHubException {
        try {
            List<GHEventInfo> events = gitHubBuilder.build().getEvents();
            List<GHEventInfo> filteredEvents = getFilteredEvents(events);
            List<GHRepository> repositories = getRepositories(filteredEvents);

            repositories.forEach(repo -> manager.tell(repo, self()));

            if (!CollectionUtils.isEmpty(filteredEvents)) {
                latestRepo = filteredEvents.get(0).getRepository();
                latestEventMillis = filteredEvents.get(0).getCreatedAt().getTime();
            }
            System.out.println("done");
        } catch (IOException e) {
            throw new GitHubException(e);
        }
    }

    private List<GHEventInfo> getFilteredEvents(List<GHEventInfo> events) {
        return events.stream().filter(event -> event.getType() == GHEvent.CREATE)
                              .filter(event -> event.getCreatedAt().getTime() > latestEventMillis)
                              .collect(Collectors.toList());
    }

    private List<GHRepository> getRepositories(List<GHEventInfo> events) {
        return events.stream().map(this::getRepository).filter(repo -> repo != null).collect(Collectors.toList());
    }

    private GHRepository getRepository(GHEventInfo event) {
        try {
            return event.getRepository();
        } catch (IOException e) {
            return null;
        }
    }
}
