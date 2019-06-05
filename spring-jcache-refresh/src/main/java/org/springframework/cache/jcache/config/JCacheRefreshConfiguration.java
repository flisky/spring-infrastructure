package org.springframework.cache.jcache.config;

import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.aspectj.JCacheCacheAspect;
import org.springframework.cache.jcache.interceptor.CacheBaseRefreshInterceptor;
import org.springframework.cache.jcache.interceptor.CachePutRefreshInterceptor;
import org.springframework.cache.jcache.interceptor.CacheResultRefreshInterceptor;
import org.springframework.cache.jcache.support.JCacheExpiryDuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

@Configuration
@EnableConfigurationProperties(JCacheRefreshProperties.class)
public class JCacheRefreshConfiguration {

    @Bean
    public JCacheManagerCustomizer customizer(JCacheRefreshProperties properties, JCacheExpiryDuration expiryDuration) {
        JCacheCacheAspect aspect = JCacheCacheAspect.aspectOf();
        Field resultField = ReflectionUtils.findField(JCacheCacheAspect.class, "cacheResultInterceptor");
        Assert.notNull(resultField, "cacheResultInterceptor field");
        resultField.setAccessible(true);
        CacheResultRefreshInterceptor resultInterceptor = new CacheResultRefreshInterceptor(
                CacheBaseRefreshInterceptor.ofResult(properties.getExpiryFactor(), properties.getEternalExpiry(), expiryDuration),
                properties.getExecutionJitter(), properties.getExecutionTimeout(), aspect.getErrorHandler());
        ReflectionUtils.setField(resultField, aspect, resultInterceptor);
        resultField.setAccessible(false);

        Field putField = ReflectionUtils.findField(JCacheCacheAspect.class, "cachePutInterceptor");
        Assert.notNull(putField, "cachePutInterceptor field");
        putField.setAccessible(true);
        CachePutRefreshInterceptor putInterceptor = new CachePutRefreshInterceptor(
                CacheBaseRefreshInterceptor.ofPut(properties.getExpiryFactor(), properties.getEternalExpiry(), expiryDuration),
                aspect.getErrorHandler());
        ReflectionUtils.setField(putField, aspect, putInterceptor);
        putField.setAccessible(false);

        return cacheManager -> {
        };
    }
}
