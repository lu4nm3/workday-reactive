package com.workday.reactive;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.workday.reactive.configuration.TwitterConfig;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuth2Token;

import java.io.File;
import java.util.Map;

/**
 * @author lmedina
 */
public class Application {
    private Config configuration;

    private Twitter twitter;

    public static void main(String[] args) {
        Application app = new Application();
        app.init();

        app.twitterTest();
    }

    private void init() {
        configuration = ConfigFactory.parseFile(new File("configuration/application.conf"));//.withFallback(ConfigFactory.load());

        twitter = getTwitter();

    }

    private Twitter getTwitter() {
        AccessToken accessToken = new AccessToken(configuration.getString(TwitterConfig.Auth.ACCESS_TOKEN),
                                                  configuration.getString(TwitterConfig.Auth.ACCESS_TOKEN_SECRET));

        TwitterFactory factory = new TwitterFactory();

        Twitter twitter = factory.getInstance();
        twitter.setOAuthConsumer(configuration.getString(TwitterConfig.Auth.API_KEY),
                                 configuration.getString(TwitterConfig.Auth.API_SECRET));
        twitter.setOAuthAccessToken(accessToken);

        return twitter;
    }

    private void twitterTest() {
        Query query = new Query("reactive");
        try {
            // 180 requests per 15-minutes
            QueryResult result = twitter.search(query);
            result.getTweets().stream().forEach(tweet -> System.out.println(tweet.getText()));
        } catch (TwitterException e) {
            System.out.println(e);
        }

        try {
            Map<String, RateLimitStatus> rateLimitStatus = twitter.getRateLimitStatus("search");
            System.out.println(rateLimitStatus);
        } catch (TwitterException e) {
            e.printStackTrace();
        }
    }
}
