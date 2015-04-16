package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.actor.messages.Start;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedSearchIterable;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.util.Iterator;

import static com.workday.reactive.Constants.*;

/**
 * @author lmedina
 */
public class GitHubEventsListenerActor extends AbstractLoggingActor {
    private GitHubBuilder gitHubBuilder;
    private RateLimiter gitHubRateLimiter;

    private ActorRef manager;

    public static Props props(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, ActorRef manager) {
        return Props.create(GitHubEventsListenerActor.class, gitHubBuilder, gitHubRateLimiter, manager);
    }

    GitHubEventsListenerActor(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, ActorRef manager) {
        this.gitHubBuilder = gitHubBuilder;
        this.gitHubRateLimiter = gitHubRateLimiter;
        this.manager = manager;
    }

    @Override
    public void preStart() {
        loadCurrentGitHubRepositories();
    }

    private void loadCurrentGitHubRepositories() {
        try {
            GitHub gitHub = gitHubBuilder.build();

            PagedSearchIterable<GHRepository> searchIterable = gitHub.searchRepositories().q(REACTIVE).list();

            Iterator<GHRepository> iterator = searchIterable.iterator();

            System.out.println(searchIterable.getTotalCount());
            int count = 0;
            while (iterator.hasNext()) {
                gitHubRateLimiter.acquire();
                GHRepository repository = iterator.next();
                manager.tell(repository, self());
                count++;
                System.out.println("count = " + count + " | repo = " + repository.getFullName());
            }

            System.out.println(searchIterable.getTotalCount());
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    @Override
    public void postRestart(Throwable reason) {
        // Overriding postRestart to disable the call to preStart() after restarts in order to prevent.
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(Start.class, msg -> start())
                .build();
    }

    private void start() {

    }
}
