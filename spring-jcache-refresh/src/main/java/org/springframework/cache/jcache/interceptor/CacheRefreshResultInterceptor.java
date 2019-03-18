package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.JCacheRefreshCache;
import org.springframework.cache.jcache.support.EpochValueWrapper;
import org.springframework.cache.jcache.support.JCacheExpiryDuration;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class CacheRefreshResultInterceptor extends CacheResultInterceptor {
    private final double expiryFactor;
    private final Duration externalExpiry;
    private final Duration executionTimeout;
    private final JCacheExpiryDuration expiryDuration;

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);
    private final javax.cache.Cache<String, Boolean> bustCache;

    public CacheRefreshResultInterceptor(double expiryFactor,
                                         Duration externalExpiry,
                                         Duration executionTimeout,
                                         JCacheExpiryDuration expiryDuration,
                                         CacheErrorHandler errorHandler) {
        super(errorHandler);
        this.expiryFactor = expiryFactor;
        this.externalExpiry = externalExpiry;
        this.executionTimeout = executionTimeout;
        this.expiryDuration = expiryDuration;
        this.bustCache = createBustCache(executionTimeout);
    }

    @Override
    @Nullable
    protected Object invoke(
            CacheOperationInvocationContext<CacheResultOperation> context, CacheOperationInvoker invoker) {

        CacheResultOperation operation = context.getOperation();
        Object cacheKey = generateKey(context);

        Cache cache = resolveCache(context);

        if (!operation.isAlwaysInvoked()) {
            Cache.ValueWrapper cachedValue = doGet(cache, cacheKey);
            if (cachedValue != null) {
                if (cachedValue instanceof EpochValueWrapper) {
                    if (((EpochValueWrapper) cachedValue).isExpired() && !inFlight(cache, cacheKey)) {
                        Mono<Object> mono = Mono.fromRunnable(() -> invoke(context, invoker, cache, cacheKey)).ignoreElement();
                        if (!executionTimeout.isZero()) {
                            mono = mono.timeout(executionTimeout);
                        }
                        mono.doOnTerminate(() -> bustCache.remove(flightKey(cache, cacheKey))).subscribe();
                    }
                }
                return cachedValue.get();
            }
            Cache exceptionCache = resolveExceptionCache(context);
            checkForCachedException(exceptionCache, cacheKey);
        }

        return invoke(context, invoker, cache, cacheKey);
    }

    private Object invoke(CacheOperationInvocationContext<CacheResultOperation> context, CacheOperationInvoker invoker,
                          Cache cache, Object cacheKey) {
        try {
            Object invocationResult = invoker.invoke();
            doPut(cache, cacheKey, invocationResult);
            return invocationResult;
        } catch (CacheOperationInvoker.ThrowableWrapper ex) {
            Throwable original = ex.getOriginal();
            Cache exceptionCache = resolveExceptionCache(context);
            cacheException(exceptionCache, context.getOperation().getExceptionTypeFilter(), cacheKey, original);
            throw ex;
        }
    }

    @Override
    protected Cache resolveCache(CacheOperationInvocationContext<CacheResultOperation> context) {
        String name = context.getOperation().getCacheName();
        Cache cache = this.cacheMap.get(name);
        if (cache != null) {
            return cache;
        } else {
            // Fully synchronize now for missing cache creation...
            synchronized (this.cacheMap) {
                cache = this.cacheMap.get(name);
                if (cache == null) {
                    cache = super.resolveCache(context);
                    if (cache != null) {
                        this.cacheMap.put(name, decorateCache((JCacheCache) cache));
                    }
                }
                return cache;
            }
        }
    }

    private Cache decorateCache(JCacheCache cache) {
        javax.cache.expiry.Duration duration = expiryDuration.getDuration(cache.getNativeCache());
        if (duration.isZero()) {
            return cache;
        }
        if (duration.isEternal()) {
            duration = new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, externalExpiry.toMillis());
        } else {
            double millis = duration.getTimeUnit().toMillis(duration.getDurationAmount()) * expiryFactor;
            duration = new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, Double.valueOf(millis).longValue());
        }
        return new JCacheRefreshCache(cache.getNativeCache(), duration, cache.isAllowNullValues());
    }


    private javax.cache.Cache<String, Boolean> createBustCache(Duration executionTimeout) {
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Boolean> config = new MutableConfiguration<>();
        if (executionTimeout.isZero()) {
            executionTimeout = Duration.ofSeconds(15);
        }
        javax.cache.expiry.Duration duration = new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, executionTimeout.toMillis());
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(duration));
        return manager.createCache("spring-jcache-refresh", config);
    }

    private String flightKey(Cache cache, Object cacheKey) {
        return cache.getName() + "--" + cacheKey.hashCode();
    }

    private boolean inFlight(Cache cache, Object cacheKey) {
        String key = flightKey(cache, cacheKey);
        Boolean inflight = bustCache.get(key);
        if (Boolean.TRUE.equals(inflight)) {
            return true;
        }
        bustCache.put(key, true);
        return false;
    }

    @Nullable
    private Cache resolveExceptionCache(CacheOperationInvocationContext<CacheResultOperation> context) {
        CacheResolver exceptionCacheResolver = context.getOperation().getExceptionCacheResolver();
        if (exceptionCacheResolver != null) {
            return extractFrom(context.getOperation().getExceptionCacheResolver().resolveCaches(context));
        }
        return null;
    }
}
