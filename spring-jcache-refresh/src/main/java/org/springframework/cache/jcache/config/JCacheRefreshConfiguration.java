package org.springframework.cache.jcache.config;

import org.springframework.cache.aspectj.JCacheCacheAspect;
import org.springframework.cache.jcache.interceptor.CacheRefreshResultInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.time.Duration;

@Configuration
public class JCacheRefreshConfiguration {

    @PostConstruct
    public void updateInterceptor() {
        JCacheCacheAspect aspect = JCacheCacheAspect.aspectOf();
        Field field = ReflectionUtils.findField(JCacheCacheAspect.class, "cacheResultInterceptor");
        Assert.notNull(field, "cacheResultInterceptor field");
        field.setAccessible(true);
        ReflectionUtils.setField(field, aspect, new CacheRefreshResultInterceptor(aspect.getErrorHandler(), Duration.ofHours(1)));
        field.setAccessible(false);
    }
}
