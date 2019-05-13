package org.springframework.cache.jcache.interceptor;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;

import javax.cache.annotation.CachePut;

public class CachePutRefreshInterceptor extends CachePutInterceptor {
    private final CacheBaseRefreshInterceptor<CachePutOperation, CachePut> refreshInterceptor;

    public CachePutRefreshInterceptor(CacheBaseRefreshInterceptor<CachePutOperation, CachePut> refreshInterceptor,
                                      CacheErrorHandler errorHandler) {
        super(errorHandler);
        this.refreshInterceptor = refreshInterceptor;
    }

    @Override
    protected void cacheValue(CacheOperationInvocationContext<CachePutOperation> context, Object value) {
        Object key = generateKey(context);
        Cache cache = refreshInterceptor.resolveRefreshCache(context);
        doPut(cache, key, value);
    }
}
