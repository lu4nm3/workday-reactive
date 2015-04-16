package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;
import com.workday.reactive.actor.messages.*;
import com.workday.reactive.configuration.TwitterConfig;
import org.kohsuke.github.GHRepository;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;
import twitter4j.*;
import twitter4j.auth.AccessToken;

import java.util.concurrent.TimeUnit;

import static com.workday.reactive.Constants.REACTIVE;

/**
 * @author lmedina
 */
public class WorkerActor extends AbstractLoggingActor {
    private Twitter twitter;

    private ActorRef twitterThrottler;
    private ActorRef manager;

    private GHRepository currentRepository;

    public static Props props(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager) {
        return Props.create(WorkerActor.class, twitterFactory, twitterThrottler, manager);
    }

    WorkerActor(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager) {
        this.twitterThrottler = twitterThrottler;
        this.manager = manager;

        initializeTwitter(twitterFactory);
        manager.tell(new NewWorker(), self());
    }

    private void initializeTwitter(TwitterFactory twitterFactory) {
        String consumerKey = context().system().settings().config().getString(TwitterConfig.Auth.API_KEY);
        String consumerSecret = context().system().settings().config().getString(TwitterConfig.Auth.API_SECRET);
        String token = context().system().settings().config().getString(TwitterConfig.Auth.ACCESS_TOKEN);
        String tokenSecret = context().system().settings().config().getString(TwitterConfig.Auth.ACCESS_TOKEN_SECRET);
        AccessToken accessToken = new AccessToken(token, tokenSecret);

        twitter = twitterFactory.getInstance();
        twitter.setOAuthConsumer(consumerKey, consumerSecret);
        twitter.setOAuthAccessToken(accessToken);
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(WorkAvailable.class, msg -> requestWork())
                .match(GHRepository.class, this::getToWork)
                .build();
    }

    private void requestWork() {
        sender().tell(new NeedWork(), self());
    }

    private void getToWork(GHRepository repository) {
        currentRepository = repository;
        requestToken();
        context().become(working);
    }

    private void requestToken() {
        twitterThrottler.tell(new NeedToken(), self());
    }

    private PartialFunction<Object, BoxedUnit> working = ReceiveBuilder
            .match(RateToken.class, msg -> processRepository())
            .match(NoMoreTokens.class, msg -> context().setReceiveTimeout(Duration.create(1, TimeUnit.SECONDS)))
            .match(ReceiveTimeout.class, msg -> tryAgain())
            .build();

    private void processRepository() {
        try {
            // 450 requests per 15-minutes
            Query query = new Query(REACTIVE);
            QueryResult result = twitter.search(query);
            result.getTweets().stream().forEach(tweet -> System.out.println(tweet.getText()));
            manager.tell(new WorkDone(), self());
        } catch (TwitterException e) {
            System.out.println(e);
        }

        sender().tell(new WorkDone(), self());
    }

    private void tryAgain() {
        context().setReceiveTimeout(Duration.Undefined());
        requestToken();
    }



}
