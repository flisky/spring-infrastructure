package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.JCacheRefreshCache;
import org.springframework.cache.jcache.support.JCacheExpiryDuration;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import javax.cache.annotation.CachePut;
import javax.cache.annotation.CacheResult;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class CacheBaseRefreshInterceptor<O extends AbstractJCacheOperation<A>, A extends Annotation> {
    private final double expiryFactor;
    private final Duration eternalExpiry;
    private final JCacheExpiryDuration expiryDuration;
    private final ConcurrentMap<O, Cache> cacheMap = new ConcurrentHashMap<>(16);

    private CacheBaseRefreshInterceptor(double expiryFactor, Duration eternalExpiry, JCacheExpiryDuration expiryDuration) {
        this.expiryFactor = expiryFactor;
        this.eternalExpiry = eternalExpiry;
        this.expiryDuration = expiryDuration;
    }

    public static CacheBaseRefreshInterceptor<CacheResultOperation, CacheResult> ofResult(double expiryFactor, Duration eternalExpiry, JCacheExpiryDuration expiryDuration) {
        return new CacheBaseRefreshInterceptor<>(expiryFactor, eternalExpiry, expiryDuration);
    }

    public static CacheBaseRefreshInterceptor<CachePutOperation, CachePut> ofPut(double expiryFactor, Duration eternalExpiry, JCacheExpiryDuration expiryDuration) {
        return new CacheBaseRefreshInterceptor<>(expiryFactor, eternalExpiry, expiryDuration);
    }

    /**
     * Convert the collection of caches in a single expected element.
     * <p>Throw an {@link IllegalStateException} if the collection holds more than one element
     *
     * @return the single element or {@code null} if the collection is empty
     */
    @Nullable
    static Cache extractFrom(Collection<? extends Cache> caches) {
        if (CollectionUtils.isEmpty(caches)) {
            return null;
        } else if (caches.size() == 1) {
            return caches.iterator().next();
        } else {
            throw new IllegalStateException("Unsupported cache resolution result " + caches +
                    ": JSR-107 only supports a single cache.");
        }
    }

    public Cache resolveRefreshCache(CacheOperationInvocationContext<O> context) {
        O operation = context.getOperation();
        Cache cache = cacheMap.get(operation);
        if (cache != null) {
            return cache;
        }
        // Fully synchronize now for missing cache creation...
        synchronized (cacheMap) {
            cache = cacheMap.get(operation);
            if (cache == null) {
                cache = this.resolveCache(context);
                if (cache instanceof JCacheCache) {
                    cache = decorateCache((JCacheCache) cache);
                }
                cacheMap.put(operation, cache);
            }
            return cache;
        }
    }

    private Cache decorateCache(JCacheCache cache) {
        javax.cache.expiry.Duration duration = expiryDuration.getDuration(cache.getNativeCache());
        if (duration.isEternal()) {
            duration = new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, eternalExpiry.toMillis());
        } else {
            double millis = duration.getTimeUnit().toMillis(duration.getDurationAmount()) * expiryFactor;
            duration = new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, Double.valueOf(millis).longValue());
        }
        if (duration.isZero()) {
            return cache;
        }
        return new JCacheRefreshCache(cache.getNativeCache(), duration, cache.isAllowNullValues());
    }

    /**
     * Resolve the cache to use.
     *
     * @param context the invocation context
     * @return the cache to use (never null)
     */
    protected Cache resolveCache(CacheOperationInvocationContext<O> context) {
        Collection<? extends Cache> caches = context.getOperation().getCacheResolver().resolveCaches(context);
        Cache cache = extractFrom(caches);
        if (cache == null) {
            throw new IllegalStateException("Cache could not have been resolved for " + context.getOperation());
        }
        return cache;
    }

}
