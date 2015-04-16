package com.workday.reactive;

/**
 * @author lmedina
 */
public class GitHubException extends Exception {
    public GitHubException() {
        super();
    }

    public GitHubException(String message) {
        super(message);
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitHubException(Throwable cause) {
        super(cause);
    }
}
