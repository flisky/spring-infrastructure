package org.springframework.cache.jcache.config;

import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.aspectj.JCacheCacheAspect;
import org.springframework.cache.jcache.interceptor.CacheRefreshResultInterceptor;
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
        Field field = ReflectionUtils.findField(JCacheCacheAspect.class, "cacheResultInterceptor");
        Assert.notNull(field, "cacheResultInterceptor field");
        field.setAccessible(true);
        CacheRefreshResultInterceptor interceptor = new CacheRefreshResultInterceptor(properties.getExpiryFactor(),
                properties.getEternalExpiry(), properties.executionTimeout, expiryDuration, aspect.getErrorHandler());
        ReflectionUtils.setField(field, aspect, interceptor);
        field.setAccessible(false);

        return cacheManager -> {
        };
    }
}
