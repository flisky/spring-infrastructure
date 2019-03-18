package org.springframework.cache.jcache.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.cache.Cache;

import java.io.Serializable;
import java.time.Duration;

@Builder
@AllArgsConstructor
public class EpochValueWrapper implements Cache.ValueWrapper, Serializable {
    @Getter
    private long epoch;

    private Object value;

    @Override
    public Object get() {
        return value;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > epoch;
    }

    public EpochValueWrapper(Object value, Duration duration) {
        this(System.currentTimeMillis() + duration.toMillis(), value);
    }
}
