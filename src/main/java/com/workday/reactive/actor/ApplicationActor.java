package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedSearchIterable;
import twitter4j.TwitterFactory;

import java.io.IOException;
import java.util.Iterator;

import static com.workday.reactive.Constants.MANAGER_ACTOR;

/**
 * @author lmedina
 */
public class ApplicationActor extends AbstractLoggingActor{
    private GitHubBuilder gitHubBuilder;
    private TwitterFactory twitterFactory;

    private ActorRef manager;

    public static Props props(GitHubBuilder gitHubBuilder, TwitterFactory twitterFactory) {
        return Props.create(ApplicationActor.class, gitHubBuilder, twitterFactory);
    }

    ApplicationActor(GitHubBuilder gitHubBuilder, TwitterFactory twitterFactory) {
        this.gitHubBuilder = gitHubBuilder;
        this.twitterFactory = twitterFactory;

        manager = context().actorOf(ManagerActor.props(), MANAGER_ACTOR);
    }

    private void getCurrentReactiveRepos() {
        try {
            GitHub gitHub = gitHubBuilder.build();

            PagedSearchIterable<GHRepository> searchIterable = gitHub.searchRepositories().q("reactive").list();//.q("reactive").list();

            Iterator<GHRepository> iterator = searchIterable.iterator();
            while (iterator.hasNext()) {
                System.out.println(iterator.next().getFullName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
