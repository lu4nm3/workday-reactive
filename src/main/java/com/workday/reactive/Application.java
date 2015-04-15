package com.workday.reactive;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.workday.reactive.configuration.GitHubConfig;
import com.workday.reactive.configuration.TwitterConfig;
import org.kohsuke.github.*;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuth2Token;
import twitter4j.conf.PropertyConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * @author lmedina
 */
public class Application {
    private ActorSystem system;

    private Config configuration;

    private GitHub gitHub;
    private GitHubBuilder gitHubBuilder;
    private Twitter twitter;

    public static void main(String[] args) {
        Application app = new Application();
        app.init();

        app.gitHubTest();
//        app.twitterTest();
    }

    private void init() {
        configuration = ConfigFactory.parseFile(new File("configuration/application.conf"));//.withFallback(ConfigFactory.load());

        gitHubBuilder = getGitHubBuilder();

        twitter = getTwitter();
    }

    private GitHubBuilder getGitHubBuilder() {
        Properties properties = new Properties();
//        properties.put("oauth", configuration.getString(GitHubConfig.ACCESS_TOKEN));
//        properties.put("login", configuration.getString(GitHubConfig.LOGIN));
//        properties.put("password", configuration.getString(GitHubConfig.PASSWORD));

        return GitHubBuilder.fromProperties(properties);
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

    private void gitHubTest() {
        try {
            GitHub gitHub = gitHubBuilder.build();

            PagedSearchIterable<GHRepository> searchIterable = gitHub.searchRepositories().q("reactive").list();//.q("reactive").list();

            Iterator<GHRepository> iterator = searchIterable.iterator();
            while (iterator.hasNext()) {
                System.out.println(iterator.next().getFullName());
            }
//            searchIterable.asList().stream().forEach(repo -> System.out.println(repo.getFullName()));

            System.out.println(searchIterable.getTotalCount());
        } catch (IOException e) {
            System.out.println(e);
        }
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
