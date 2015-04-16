package com.workday.reactive.actor;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.FromConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.GitHubException;
import com.workday.reactive.actor.messages.Start;
import com.workday.reactive.ioc.AbstractFactory;
import com.workday.reactive.retry.ExponentialBackOffRetryable;
import org.kohsuke.github.GitHubBuilder;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import java.util.concurrent.TimeUnit;

import static com.workday.reactive.Constants.*;

/**
 * @author lmedina
 */
public class ApplicationActor extends AbstractLoggingActor{
    private ActorRef manager;
    private ActorRef twitterThrottler;
    private ActorRef workers;
    private ActorRef eventsListener;

    private SupervisorStrategy strategy = new OneForOneStrategy(3, Duration.create(1, TimeUnit.MINUTES), DeciderBuilder
            .match(GitHubException.class, e -> {
                System.out.println("GitHub appears to be unreachable. Try again later.");
                System.exit(-1);
                return SupervisorStrategy.stop();
            })
            .match(TwitterException.class, e -> {
                System.out.println("Twitter appears to be unreachable. Try again later.");
                System.exit(-1);
                return SupervisorStrategy.stop();
            })
            .match(Throwable.class, e -> {
                log().warning(e.getMessage());
                return SupervisorStrategy.restart();
            }).build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    public static Props props(GitHubBuilder gitHubBuilder,
                              RateLimiter gitHubRateLimiter,
                              TwitterFactory twitterFactory,
                              ObjectMapper objectMapper,
                              AbstractFactory<ExponentialBackOffRetryable> twitterRetryableFactory) {
        return Props.create(ApplicationActor.class, gitHubBuilder, gitHubRateLimiter, twitterFactory, objectMapper, twitterRetryableFactory);
    }

    ApplicationActor(GitHubBuilder gitHubBuilder,
                     RateLimiter gitHubRateLimiter,
                     TwitterFactory twitterFactory,
                     ObjectMapper objectMapper,
                     AbstractFactory<ExponentialBackOffRetryable> twitterRetryableFactory) {
        manager = context().actorOf(ManagerActor.props(), MANAGER_ACTOR);
        twitterThrottler = context().actorOf(ThrottlingActor.props(), THROTTLING_ACTOR);
        workers = context().actorOf(FromConfig.getInstance().props(WorkerActor.props(twitterFactory,
                                                                                     twitterThrottler,
                                                                                     manager,
                                                                                     objectMapper,
                                                                                     twitterRetryableFactory.create())), TWITTER_WORKERS);
        eventsListener = context().actorOf(GitHubEventsListenerActor.props(gitHubBuilder, gitHubRateLimiter, manager), GITHUB_EVENTS_LISTENER_ACTOR);
        context().watch(workers);
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(Start.class, msg -> start())
                .match(Terminated.class, msg -> handleWorkerFailure())
                .build();
    }

    private void start() {
        eventsListener.tell(new Start(), self());
    }

    private void handleWorkerFailure() {
        self().tell(akka.actor.Kill.getInstance(), self());
        log().info("Shutting down application...");
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
//        context().system().shutdown();
        System.exit(-1);
    }
}
