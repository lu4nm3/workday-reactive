package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
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
    private RateLimiter rateLimiter;
    private ActorRef manager;
    private ObjectMapper mapper;
    private Retryable retryable;

    private Twitter twitter;
    private GHRepository currentRepository;

    public static Props props(TwitterFactory twitterFactory, RateLimiter rateLimiter, ActorRef manager, ObjectMapper objectMapper, Retryable retryable) {
        return Props.create(WorkerActor.class, twitterFactory, rateLimiter, manager, objectMapper, retryable);
    }

    WorkerActor(TwitterFactory twitterFactory, RateLimiter rateLimiter, ActorRef manager, ObjectMapper mapper, Retryable retryable) {
        this.rateLimiter = rateLimiter;
        this.manager = manager;
        this.mapper = mapper;
        this.retryable = retryable;

        initializeTwitter(twitterFactory);
        manager.tell(new NewWorker(), self());
        log().info("Registering with ManagerActor.");
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
                .match(WorkAvailable.class, msg -> sender().tell(new NeedWork(), self()))
                .match(GHRepository.class, this::handleRepository)
                .build();
    }

    private void handleRepository(GHRepository repository) throws TwitterException {
        try {
            processRepository(repository);
        } catch (Throwable e) {
            log().warning("There was an issue connecting to Twitter. Retrying...");
            context().become(retrying);
            currentRepository = repository;
            retry(e);
        }
    }

    private void processRepository(GHRepository repository) throws TwitterException {
        print(repository, query(repository));
        manager.tell(new WorkDone(), self());
    }

    private List<Status> query(GHRepository repository) throws TwitterException {
        Query query = new Query(repository.getFullName());
        rateLimiter.acquire();
        QueryResult result = twitter.search(query);
        return result.getTweets();
    }

    private void print(GHRepository repository, List<Status> tweets) {
        List<Tweet> customTweets = tweets.stream().map(Tweet::new).collect(Collectors.toList());
        String project = getAsJson(new Project(new Summary(repository), customTweets));
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

    private void retry(Throwable e) throws TwitterException {
        if (retryable.shouldRetry()) {
            long waitTime = retryable.incrementRetryCountAndGetWaitTime();
            context().setReceiveTimeout(Duration.create(waitTime, TimeUnit.SECONDS));
        } else {
            throw new TwitterException(e.getMessage());
        }
    }

    private void retryQuery() throws TwitterException {
        try {
            processRepository(currentRepository);
            retryable.reset();
            context().unbecome();
            context().setReceiveTimeout(Duration.Undefined());
        } catch (Throwable e) {
            log().warning("There was an issue while re-connecting to Twitter. Retrying...");
            context().setReceiveTimeout(Duration.Undefined());
            retry(e);
        }
    }
}
