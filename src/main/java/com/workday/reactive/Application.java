package com.workday.reactive;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.workday.reactive.actor.ApplicationActor;
import com.workday.reactive.configuration.TwitterConfig;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedSearchIterable;
import twitter4j.*;
import twitter4j.auth.AccessToken;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static com.workday.reactive.Constants.APPLICATION_ACTOR;

/**
 * @author lmedina
 */
@Slf4j
public class Application {
    private Config configuration;
    private ActorSystem system;

    private GitHubBuilder gitHubBuilder;
    private TwitterFactory twitterFactory;
    private Twitter twitter;

    public static void main(String[] args) {
        Application app = new Application();
        app.init();
        app.twitterTest();
//        app.start();
    }

    private void init() {
        configuration = ConfigFactory.parseFile(new File("configuration/application.conf"));//.withFallback(ConfigFactory.load());
        system = ActorSystem.create("ActorSystem", configuration);

        gitHubBuilder = getGitHubBuilder();
        twitterFactory = new TwitterFactory();
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

        Twitter twitter = twitterFactory.getInstance();
        twitter.setOAuthConsumer(configuration.getString(TwitterConfig.Auth.API_KEY),
                                 configuration.getString(TwitterConfig.Auth.API_SECRET));
        twitter.setOAuthAccessToken(accessToken);

//        System.out.println(accessToken.toString());
//        Configuration config = new ConfigurationBuilder()
////                .setOAuthAccessToken(configuration.getString(TwitterConfig.Auth.ACCESS_TOKEN))
////                .setOAuthAccessTokenSecret(configuration.getString(TwitterConfig.Auth.ACCESS_TOKEN_SECRET))
//                .setOAuthConsumerKey(configuration.getString(TwitterConfig.Auth.API_KEY))
//                .setOAuthConsumerSecret(configuration.getString(TwitterConfig.Auth.API_SECRET))
//                .setOAuth2AccessToken(accessToken.toString())
//                .build();
//        Authorization auth = new OAuth2Authorization(config);
//        Twitter twitter = twitterFactory.getInstance(auth);


        return twitter;
    }

    private void start() {
        log.info("Starting ManagerActor");
        ActorRef application = system.actorOf(ApplicationActor.props(gitHubBuilder, twitterFactory), APPLICATION_ACTOR);


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
