package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.GitHubException;
import com.workday.reactive.actor.messages.Initialize;
import com.workday.reactive.actor.messages.Start;
import org.kohsuke.github.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.util.Iterator;

import static com.workday.reactive.Constants.REACTIVE;

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
    public void preStart() throws GitHubException {
        self().tell(new Initialize(), self());
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
            GitHub gitHub = gitHubBuilder.withRateLimitHandler(RateLimitHandler.FAIL).build();
            PagedSearchIterable<GHRepository> searchIterable = gitHub.searchRepositories().q(REACTIVE).list();
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

    private void listen() {

    }
}
