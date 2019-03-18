package org.springframework.cache.jcache;

import org.springframework.cache.jcache.support.EpochValueWrapper;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

public class JCacheRefreshCache extends JCacheCache {
    private Duration duration;

    public JCacheRefreshCache(Cache<Object, Object> cache, Duration duration) {
        this(cache, duration, true);
    }

    public JCacheRefreshCache(Cache<Object, Object> cache, Duration duration, boolean isAllowNullValues) {
        super(cache, isAllowNullValues);
        this.duration = duration;
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
        return EpochValueWrapper.builder().epoch(duration.getAdjustedTime(System.currentTimeMillis())).value(userValue).build();
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
