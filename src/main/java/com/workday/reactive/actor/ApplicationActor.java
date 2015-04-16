package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.actor.messages.Start;
import org.kohsuke.github.GitHubBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import twitter4j.TwitterFactory;

import static com.workday.reactive.Constants.GITHUB_EVENTS_LISTENER_ACTOR;
import static com.workday.reactive.Constants.MANAGER_ACTOR;
import static com.workday.reactive.Constants.TWITTER_WORKERS;

/**
 * @author lmedina
 */
public class ApplicationActor extends AbstractLoggingActor{
    private ActorRef manager;
    private ActorRef eventsListener;
    private ActorRef twitterThrottler;
    private ActorRef workers;

    public static Props props(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, TwitterFactory twitterFactory) {
        return Props.create(ApplicationActor.class, gitHubBuilder, gitHubRateLimiter, twitterFactory);
    }

    ApplicationActor(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, TwitterFactory twitterFactory) {
        manager = context().actorOf(ManagerActor.props(), MANAGER_ACTOR);
        eventsListener = context().actorOf(GitHubEventsListenerActor.props(gitHubBuilder, gitHubRateLimiter, manager), GITHUB_EVENTS_LISTENER_ACTOR);
        twitterThrottler = null;
        workers = context().actorOf(WorkerActor.props(twitterFactory, twitterThrottler, manager), TWITTER_WORKERS);
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(Start.class, msg -> start())
                .build();
    }

    private void start() {
        eventsListener.tell(new Start(), self());
    }
}
