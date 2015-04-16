package com.workday.reactive.retry;

/**
 * Implements a backoff retry with exponentially increasing time intervals.
 *
 * @author lmedina
 */
public class ExponentialBackOffRetryable extends Retryable {
    private long retryCounter;

    public ExponentialBackOffRetryable(Long waitInterval, Long incrementFactor, Long maxRetries) {
        super(waitInterval, incrementFactor, maxRetries);
    }

    @Override
    public boolean shouldRetry() {
        return retryCounter < maxRetries;
    }

    @Override
    public long incrementRetryCountAndGetWaitTime() {
        return waitInterval * Math.round(Math.pow(incrementFactor, retryCounter++));
    }

    @Override
    public void reset() {
        retryCounter = 0L;
    }
}
