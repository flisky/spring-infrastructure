package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.jcache.support.EpochValueWrapper;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.annotation.CacheResult;
import javax.cache.configuration.MutableConfiguration;
import java.time.Duration;
import java.util.Random;

public class CacheResultRefreshInterceptor extends CacheResultInterceptor {
    private static final Random random = new Random();

    private final CacheBaseRefreshInterceptor<CacheResultOperation, CacheResult> refreshInterceptor;
    private final Duration executionTimeout;
    private final int jitter;
    private final javax.cache.Cache<String, Boolean> bustCache;

    public CacheResultRefreshInterceptor(CacheBaseRefreshInterceptor<CacheResultOperation, CacheResult> refreshInterceptor,
                                         Duration jitter,
                                         Duration executionTimeout,
                                         CacheErrorHandler errorHandler) {
        super(errorHandler);
        this.refreshInterceptor = refreshInterceptor;
        this.jitter = Math.toIntExact(jitter.toMillis());
        this.executionTimeout = executionTimeout;
        this.bustCache = createBustCache();
    }

    @Override
    @Nullable
    protected Object invoke(
            CacheOperationInvocationContext<CacheResultOperation> context, CacheOperationInvoker invoker) {

        CacheResultOperation operation = context.getOperation();
        Object cacheKey = generateKey(context);

        Cache cache = refreshInterceptor.resolveRefreshCache(context);

        if (!operation.isAlwaysInvoked()) {
            Cache.ValueWrapper cachedValue = doGet(cache, cacheKey);
            if (cachedValue != null) {
                if (cachedValue instanceof EpochValueWrapper && ((EpochValueWrapper) cachedValue).isExpired() && !inFlight(cache, cacheKey)) {
                    Mono<Object> mono = Mono.fromRunnable(() -> invoke(context, invoker, cache, cacheKey)).ignoreElement().subscribeOn(Schedulers.elastic());
                    if (jitter > 0) {
                        mono = Mono.delay(Duration.ofMillis(random.nextInt(jitter))).then(mono);
                    }
                    if (!executionTimeout.isZero()) {
                        mono = mono.timeout(executionTimeout);
                    }
                    mono.doOnTerminate(() -> removeFlight(cache, cacheKey)).subscribe();
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

    private javax.cache.Cache<String, Boolean> createBustCache() {
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Boolean> config = new MutableConfiguration<>();
        return manager.createCache("spring-jcache-refresh", config);
    }

    private String flightKey(Cache cache, Object cacheKey) {
        return cache.getName() + ":" + cacheKey.toString();
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

    private void removeFlight(Cache cache, Object cacheKey) {
        bustCache.remove(flightKey(cache, cacheKey));
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
