package com.workday.reactive.configuration;

/**
 * @author lmedina
 */
public class TwitterConfig {
    public static final class Auth {
        public static final String ACCESS_TOKEN = "twitter.authentication.accessToken";
        public static final String ACCESS_TOKEN_SECRET = "twitter.authentication.accessTokenSecret";
        public static final String API_KEY = "twitter.authentication.apiKey";
        public static final String API_SECRET = "twitter.authentication.apiSecret";
    }

    public static final String RECENT_TWEET_COUNT = "twitter.recentTweetCount";
}
