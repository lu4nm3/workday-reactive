package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.FromConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.actor.messages.Start;
import org.kohsuke.github.GitHubBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import twitter4j.TwitterFactory;

import static com.workday.reactive.Constants.*;

/**
 * @author lmedina
 */
public class ApplicationActor extends AbstractLoggingActor{
    private ActorRef manager;
    private ActorRef twitterThrottler;
    private ActorRef workers;
    private ActorRef eventsListener;

    public static Props props(GitHubBuilder gitHubBuilder,
                              RateLimiter gitHubRateLimiter,
                              TwitterFactory twitterFactory,
                              ObjectMapper objectMapper) {
        return Props.create(ApplicationActor.class, gitHubBuilder, gitHubRateLimiter, twitterFactory, objectMapper);
    }

    ApplicationActor(GitHubBuilder gitHubBuilder, RateLimiter gitHubRateLimiter, TwitterFactory twitterFactory, ObjectMapper objectMapper) {
        manager = context().actorOf(ManagerActor.props(), MANAGER_ACTOR);
        twitterThrottler = context().actorOf(ThrottlingActor.props(), THROTTLING_ACTOR);
        workers = context().actorOf(FromConfig.getInstance().props(WorkerActor.props(twitterFactory, twitterThrottler, manager, objectMapper)), TWITTER_WORKERS);
        eventsListener = context().actorOf(GitHubEventsListenerActor.props(gitHubBuilder, gitHubRateLimiter, manager), GITHUB_EVENTS_LISTENER_ACTOR);
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
