package com.workday.reactive.configuration;

/**
 * @author lmedina
 */
public class GitHubConfig {
    public static final class Auth {
        public static final String ACCESS_TOKEN = "github.authentication.access-token";
        public static final String LOGIN = "github.authentication.login";
        public static final String PASSWORD = "github.authentication.password";
    }

    public static final String REQUESTS_PER_SECOND = "github.requests-per-second";
    public static final String LISTENING_INTERVAL_SECONDS = "github.listening-interval-seconds";
}
