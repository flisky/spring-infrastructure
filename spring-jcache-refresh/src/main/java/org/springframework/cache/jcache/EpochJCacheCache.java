package org.springframework.cache.jcache;

import org.springframework.cache.jcache.support.EpochValueWrapper;

import javax.cache.Cache;
import java.time.Duration;

public class EpochJCacheCache extends JCacheCache {
    private long millis;

    public EpochJCacheCache(Cache<Object, Object> cache, Duration duration) {
        this(cache, duration, true);
    }

    public EpochJCacheCache(Cache<Object, Object> cache, Duration duration, boolean isAllowNullValues) {
        super(cache, isAllowNullValues);
        this.millis = duration.toMillis();
    }

    @Override
    public ValueWrapper get(Object key) {
        return (EpochValueWrapper) getNativeCache().get(key);
    }

    @Override
    protected Object toStoreValue(Object userValue) {
        if (userValue == null && !this.isAllowNullValues()) {
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' is configured to not allow null values but null was provided");
        }
        return EpochValueWrapper.builder().epoch(System.currentTimeMillis() + millis).value(userValue).build();
    }

    @Override
    protected Object fromStoreValue(Object storeValue) {
        if (storeValue instanceof EpochValueWrapper) {
            EpochValueWrapper wrapper = (EpochValueWrapper) storeValue;
            if (wrapper.isExpired()) {
                return null;
            }
            return wrapper.get();
        }
        return storeValue;
    }
}
