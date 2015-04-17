package com.workday.reactive.retry;

/**
 * @author lmedina
 */
public abstract class Retryable {
    protected Long waitInterval;
    protected Long incrementFactor;
    protected Long maxRetries;

    protected Retryable(Long waitInterval, Long incrementFactor, Long maxRetries) {
        this.waitInterval = waitInterval;
        this.incrementFactor = incrementFactor;
        this.maxRetries = maxRetries;
    }

    abstract public long incrementRetryCountAndGetWaitTime();
    abstract public boolean shouldRetry();
    abstract public void reset();
}
