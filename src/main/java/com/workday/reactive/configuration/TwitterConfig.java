package com.workday.reactive.configuration;

/**
 * @author lmedina
 */
public class TwitterConfig {
    public static final class Auth {
        public static final String ACCESS_TOKEN = "twitter.authentication.access-token";
        public static final String ACCESS_TOKEN_SECRET = "twitter.authentication.access-token-secret";
        public static final String API_KEY = "twitter.authentication.api-key";
        public static final String API_SECRET = "twitter.authentication.api-secret";
    }

    public static final class RateLimit {
        public static final String REFRESH_TIME_WINDOW_MINUTES = "twitter.rate-limit.refresh-time-window-minutes";
        public static final String MAX_REQUESTS_PER_TIME_WINDOW = "twitter.rate-limit.max-requests-per-time-window";
    }

    public static final class Retry {
        public static final String INTERVAL_MILLIS = "twitter.retry.interval-millis";
        public static final String FACTOR = "twitter.retry.factor";
        public static final String MAX_RETRIES = "twitter.retry.max-retries";
    }
}
