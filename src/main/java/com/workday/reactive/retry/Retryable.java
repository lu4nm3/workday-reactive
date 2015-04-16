package com.workday.reactive.retry;

import org.apache.commons.lang.Validate;

/**
 * @author lmedina
 */
public abstract class Retryable {
    protected Long waitInterval;
    protected Long incrementFactor;
    protected Long maxRetries;

    protected Retryable(Long waitInterval, Long incrementFactor, Long maxRetries) {
        Validate.notNull(waitInterval, "waitInterval must not be null.");
        Validate.notNull(incrementFactor, "incrementFactor must not be null.");
        Validate.notNull(maxRetries, "maxRetries must not be null.");

        this.waitInterval = waitInterval;
        this.incrementFactor = incrementFactor;
        this.maxRetries = maxRetries;
    }

    abstract public long incrementRetryCountAndGetWaitTime();
    abstract public boolean shouldRetry();
    abstract public void reset();
}
