package com.workday.reactive.ioc;

/**
 * @author lmedina
 */
public interface AbstractFactory<T> {
    T create();
}
