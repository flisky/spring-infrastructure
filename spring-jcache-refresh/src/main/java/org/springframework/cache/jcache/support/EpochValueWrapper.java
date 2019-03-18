package org.springframework.cache.jcache.support;

import lombok.Builder;
import org.springframework.cache.Cache;

import java.io.Serializable;
import java.time.Duration;

@Builder
public class EpochValueWrapper implements Cache.ValueWrapper, Serializable {
    private Object value;

    private long epoch;

    @Override
    public Object get() {
        return value;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > epoch;
    }

    public EpochValueWrapper(Object value, long epoch) {
        this.value = value;
        this.epoch = epoch;
    }

    public EpochValueWrapper(Object value, Duration duration) {
        this(value, System.currentTimeMillis() + duration.toMillis());
    }
}
