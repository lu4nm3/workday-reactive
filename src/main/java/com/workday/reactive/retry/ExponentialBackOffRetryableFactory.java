package com.workday.reactive.retry;

import com.workday.reactive.ioc.AbstractFactory;

/**
 * @author lmedina
 */
public class ExponentialBackOffRetryableFactory implements AbstractFactory<ExponentialBackOffRetryable> {
    protected Long waitInterval;
    protected Long incrementFactor;
    protected Long maxRetries;

    public ExponentialBackOffRetryableFactory(Long waitInterval, Long incrementFactor, Long maxRetries) {
        this.waitInterval = waitInterval;
        this.incrementFactor = incrementFactor;
        this.maxRetries = maxRetries;
    }

    @Override
    public ExponentialBackOffRetryable create() {
        return new ExponentialBackOffRetryable(waitInterval, incrementFactor, maxRetries);
    }
}
