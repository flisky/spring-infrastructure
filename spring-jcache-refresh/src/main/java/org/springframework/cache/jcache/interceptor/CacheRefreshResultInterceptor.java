package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.jcache.EpochJCacheCache;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.cache.jcache.support.EpochValueWrapper;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CacheRefreshResultInterceptor extends CacheResultInterceptor {
    private final Duration executionTimeout;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

    public CacheRefreshResultInterceptor(CacheErrorHandler errorHandler, Duration executionTimeout) {
        super(errorHandler);
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
        String name = cache.getName();
        return new EpochJCacheCache(cache.getNativeCache(), Duration.ofMillis(1), cache.isAllowNullValues());
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
