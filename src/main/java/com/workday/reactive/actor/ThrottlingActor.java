package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;
import com.workday.reactive.actor.messages.NeedToken;
import com.workday.reactive.actor.messages.NoMoreTokens;
import com.workday.reactive.actor.messages.Token;
import com.workday.reactive.configuration.TwitterConfig;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

import java.util.concurrent.TimeUnit;

/**
 * @author lmedina
 */
public class ThrottlingActor extends AbstractLoggingActor {
    private Long refreshTimeWindowMinutes;
    private Long maxRequestsPerTimeWindow;

    private Long tokenDistributed;

    public static Props props() {
        return Props.create(ThrottlingActor.class);
    }

    ThrottlingActor() {
        refreshTimeWindowMinutes = context().system().settings().config().getLong(TwitterConfig.RateLimit.REFRESH_TIME_WINDOW_MINUTES);
        maxRequestsPerTimeWindow = context().system().settings().config().getLong(TwitterConfig.RateLimit.MAX_REQUESTS_PER_TIME_WINDOW);

        resetTokensDistributed();

        context().setReceiveTimeout(Duration.create(refreshTimeWindowMinutes, TimeUnit.MINUTES));
    }

    private void resetTokensDistributed() {
        tokenDistributed = 0L;
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(NeedToken.class, msg -> determineTokenAvailability())
                .match(ReceiveTimeout.class, msg -> resetTokensDistributed())
                .build();
    }

    private void determineTokenAvailability() {
        if (tokenDistributed < maxRequestsPerTimeWindow) {
            tokenDistributed++;
            sender().tell(new Token(), self());
        } else {
            sender().tell(new NoMoreTokens(), self());
        }
    }
}
