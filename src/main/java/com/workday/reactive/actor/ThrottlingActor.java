package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.workday.reactive.actor.messages.NewWorker;
import com.workday.reactive.actor.messages.WorkAvailable;
import com.workday.reactive.configuration.TwitterConfig;
import org.kohsuke.github.GHRepository;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

/**
 * @author lmedina
 */
public class ThrottlingActor extends AbstractLoggingActor {
    private Integer refreshTimeWindowMinutes;
    private Long maxRequestsPerTimeWindow;

    public static Props props() {
        return Props.create(ThrottlingActor.class);
    }

    ThrottlingActor() {
        refreshTimeWindowMinutes = context().system().settings().config().getInt(TwitterConfig.RateLimit.MAX_REQUESTS_PER_TIME_WINDOW);
        maxRequestsPerTimeWindow = context().system().settings().config().getLong(TwitterConfig.RateLimit.MAX_REQUESTS_PER_TIME_WINDOW);
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
}
