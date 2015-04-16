package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.workday.reactive.actor.messages.*;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import twitter4j.TwitterFactory;

/**
 * @author lmedina
 */
public class WorkerActor extends AbstractLoggingActor {
    private TwitterFactory twitterFactory;

    private ActorRef twitterThrottler;
    private ActorRef manager;

    public static Props props(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager) {
        return Props.create(WorkerActor.class, twitterFactory, twitterThrottler, manager);
    }

    WorkerActor(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager) {
        this.twitterFactory = twitterFactory;
        this.twitterThrottler = twitterThrottler;
        this.manager = manager;

        manager.tell(new NewWorker(), self());
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(GHRepository.class, this::processRepository)
                .match(WorkAvailable.class, this::requestWork)
                .match(Terminated.class, any -> handleWorkerFailure())
                .build();
    }

    private void processRepository(GHRepository repository) {


        sender().tell(new WorkDone(), self());
    }
}
