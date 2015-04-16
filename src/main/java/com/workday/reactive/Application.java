package com.workday.reactive;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.workday.reactive.actor.ApplicationActor;
import com.workday.reactive.actor.messages.Start;
import com.workday.reactive.configuration.GitHubConfig;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterFactory;

import java.io.File;
import java.util.Properties;

import static com.workday.reactive.Constants.APPLICATION_ACTOR;

/**
 * @author lmedina
 */
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private Config configuration;
    private ActorSystem system;

    private GitHubBuilder gitHubBuilder;
    private TwitterFactory twitterFactory;
    private RateLimiter gitHubRateLimiter;
    private ObjectMapper objectMapper;

    public static void main(String[] args) {
        Application app = new Application();
        app.init();
        app.start();
    }

    private void init() {
        configuration = ConfigFactory.parseFile(new File("configuration/application.conf"));
        system = ActorSystem.create("ActorSystem", configuration);

        gitHubBuilder = getGitHubBuilder();
        twitterFactory = new TwitterFactory();
        gitHubRateLimiter = RateLimiter.create(configuration.getDouble(GitHubConfig.REQUESTS_PER_SECOND));
        objectMapper = new ObjectMapper();
    }

    private GitHubBuilder getGitHubBuilder() {
        Properties properties = new Properties();
        properties.put("oauth", configuration.getString(GitHubConfig.ACCESS_TOKEN));
        return GitHubBuilder.fromProperties(properties);
    }

    private void start() {
        log.info("Starting ManagerActor");
        ActorRef application = system.actorOf(ApplicationActor.props(gitHubBuilder, gitHubRateLimiter, twitterFactory, objectMapper), APPLICATION_ACTOR);
        application.tell(new Start(), ActorRef.noSender());
    }

//    private void gitHubTest() {
//        try {
//            GitHub gitHub = gitHubBuilder.build();
//
//            PagedSearchIterable<GHRepository> searchIterable = gitHub.searchRepositories().q("reactive").list();//.q("reactive").list();
//
//            Iterator<GHRepository> iterator = searchIterable.iterator();
//            while (iterator.hasNext()) {
//                System.out.println(iterator.next().getFullName());
//            }
////            searchIterable.asList().stream().forEach(repo -> System.out.println(repo.getFullName()));
//
//            System.out.println(searchIterable.getTotalCount());
//        } catch (IOException e) {
//            System.out.println(e);
//        }
//    }
//
//    private void twitterTest() {
//        Query query = new Query("reactive");
//        try {
//            // 180 requests per 15-minutes
//            QueryResult result = twitter.search(query);
//            result.getTweets().stream().forEach(tweet -> System.out.println(tweet.getText()));
//        } catch (TwitterException e) {
//            System.out.println(e);
//        }
//
//        try {
//            Map<String, RateLimitStatus> rateLimitStatus = twitter.getRateLimitStatus("search");
//            System.out.println(rateLimitStatus);
//        } catch (TwitterException e) {
//            e.printStackTrace();
//        }
//    }
}
