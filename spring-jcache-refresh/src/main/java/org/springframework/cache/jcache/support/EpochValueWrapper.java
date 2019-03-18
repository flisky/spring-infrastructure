package org.springframework.cache.jcache.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.cache.Cache;

import java.io.Serializable;
import java.time.Duration;

@Builder
@AllArgsConstructor
public class EpochValueWrapper<T> implements Cache.ValueWrapper, Serializable {
    private T value;

    @Getter
    private long epoch;


    public EpochValueWrapper(T value, Duration duration) {
        this(value, System.currentTimeMillis() + duration.toMillis());
    }

    @Override
    public T get() {
        return value;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > epoch;
    }
}
