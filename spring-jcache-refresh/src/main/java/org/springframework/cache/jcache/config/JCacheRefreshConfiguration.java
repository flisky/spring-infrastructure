package org.springframework.cache.jcache.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.aspectj.JCacheCacheAspect;
import org.springframework.cache.jcache.interceptor.CacheRefreshResultInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;

@Configuration
@EnableConfigurationProperties(JCacheRefreshProperties.class)
public class JCacheRefreshConfiguration {

    @PostConstruct
    public void updateInterceptor(JCacheRefreshProperties properties) {
        JCacheCacheAspect aspect = JCacheCacheAspect.aspectOf();
        Field field = ReflectionUtils.findField(JCacheCacheAspect.class, "cacheResultInterceptor");
        Assert.notNull(field, "cacheResultInterceptor field");
        field.setAccessible(true);
        CacheRefreshResultInterceptor interceptor = new CacheRefreshResultInterceptor(properties.getExpiryFactor(),
                properties.getExternalExpiry(), properties.executionTimeout, aspect.getErrorHandler());
        ReflectionUtils.setField(field, aspect, interceptor);
        field.setAccessible(false);
    }
}
