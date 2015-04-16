package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workday.reactive.actor.messages.*;
import com.workday.reactive.configuration.TwitterConfig;
import com.workday.reactive.data.Project;
import com.workday.reactive.data.Summary;
import com.workday.reactive.data.Tweet;
import com.workday.reactive.retry.Retryable;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHRepository;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;
import twitter4j.*;
import twitter4j.auth.AccessToken;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author lmedina
 */
public class WorkerActor extends AbstractLoggingActor {
    private ActorRef twitterThrottler;
    private ActorRef manager;
    private ObjectMapper mapper;
    private Retryable retryable;

    private Twitter twitter;

    private GHRepository currentRepository;

    public static Props props(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager, ObjectMapper objectMapper, Retryable retryable) {
        return Props.create(WorkerActor.class, twitterFactory, twitterThrottler, manager, objectMapper, retryable);
    }

    WorkerActor(TwitterFactory twitterFactory, ActorRef twitterThrottler, ActorRef manager, ObjectMapper mapper, Retryable retryable) {
        this.twitterThrottler = twitterThrottler;
        this.manager = manager;
        this.mapper = mapper;
        this.retryable = retryable;

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
            .match(Token.class, msg -> processRepository())
            .match(NoMoreTokens.class, msg -> context().setReceiveTimeout(Duration.create(1, TimeUnit.SECONDS)))
            .match(ReceiveTimeout.class, msg -> tryAcquiringTokenAgain())
            .build();

    private void processRepository() throws TwitterException {
        try {
            print(query());
            manager.tell(new WorkDone(), self());
            context().unbecome();
        } catch (TwitterException e) {
            log().warning("There was an issue connecting to Twitter. Retrying...");
            context().become(retrying);
            retry(e);
        }
    }

    private List<Status> query() throws TwitterException {
        Query query = new Query(currentRepository.getFullName());
        QueryResult result = twitter.search(query);
        return result.getTweets();
    }

    private void tryAcquiringTokenAgain() {
        context().setReceiveTimeout(Duration.Undefined());
        requestToken();
    }

    private void print(List<Status> tweets) {
        List<Tweet> customTweets = tweets.stream().map(Tweet::new).collect(Collectors.toList());
        String project = getAsJson(new Project(new Summary(currentRepository), customTweets));
        System.out.println(project);
    }

    private String getAsJson(Project project) {
        try {
            return mapper.writeValueAsString(project);
        } catch (JsonProcessingException e) {
            return StringUtils.EMPTY;
        }
    }

    private PartialFunction<Object, BoxedUnit> retrying = ReceiveBuilder
            .match(ReceiveTimeout.class, msg -> retryQuery())
            .build();

    private void retry(TwitterException e) throws TwitterException {
        if (retryable.shouldRetry()) {
            long waitTime = retryable.incrementRetryCountAndGetWaitTime();
            context().setReceiveTimeout(Duration.create(waitTime, TimeUnit.SECONDS));
        } else {
            throw e;
        }
    }

    private void retryQuery() throws TwitterException {
        try {
            query();
            requestToken();
            retryable.reset();
            context().unbecome();
            context().setReceiveTimeout(Duration.Undefined());
        } catch (TwitterException e) {
            context().setReceiveTimeout(Duration.Undefined());
            retry(e);
        }
    }
}
