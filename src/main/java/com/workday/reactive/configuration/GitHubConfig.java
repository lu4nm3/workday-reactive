package com.workday.reactive.configuration;

/**
 * @author lmedina
 */
public class GitHubConfig {
    public static final class Auth {
        public static final String ACCESS_TOKEN = "github.authentication.access-token";
    }

    public static final String REQUESTS_PER_SECOND = "github.requests-per-second";
    public static final String LISTENING_INTERVAL_SECONDS = "github.listening-interval-seconds";

    public static final class Retry {
        public static final String INTERVAL_MILLIS = "github.retry.interval-millis";
        public static final String FACTOR = "github.retry.factor";
        public static final String MAX_RETRIES = "github.retry.max-retries";
    }
}
