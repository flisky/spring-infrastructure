package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.jcache.JCacheRefreshCache;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.support.EpochValueWrapper;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import javax.cache.configuration.CompleteConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class CacheRefreshResultInterceptor extends CacheResultInterceptor {
    private final double expiryFactor;
    private final Duration expiryEternal;
    private final Duration executionTimeout;

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

    public CacheRefreshResultInterceptor(double expiryFactor, Duration expiryEternal, Duration executionTimeout, CacheErrorHandler errorHandler) {
        super(errorHandler);
        this.expiryFactor = expiryFactor;
        this.expiryEternal = expiryEternal;
        this.executionTimeout = executionTimeout;
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
                // extra check
                if (cachedValue instanceof EpochValueWrapper) {
                    if (((EpochValueWrapper) cachedValue).isExpired()) {
                        Mono.fromRunnable(() -> invoke(context, invoker, cache, cacheKey))
                                .ignoreElement()
                                .timeout(executionTimeout)
                                .subscribe();
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
        @SuppressWarnings("unchecked")
        CompleteConfiguration config = cache.getNativeCache().getConfiguration(CompleteConfiguration.class);
        ExpiryPolicy expiryPolicy = (ExpiryPolicy) config.getExpiryPolicyFactory().create();
        javax.cache.expiry.Duration creationExpiry = expiryPolicy.getExpiryForCreation();
        if (creationExpiry.isZero()) {
            return cache;
        }
        Duration duration;
        if (creationExpiry.isEternal()) {
            duration = expiryEternal;
        } else {
            long millis = creationExpiry.getTimeUnit().convert(creationExpiry.getDurationAmount(), TimeUnit.MILLISECONDS);
            millis = Double.valueOf(millis * expiryFactor).longValue();
            duration = Duration.ofMillis(millis);
        }
        return new JCacheRefreshCache(cache.getNativeCache(), duration, cache.isAllowNullValues());
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
