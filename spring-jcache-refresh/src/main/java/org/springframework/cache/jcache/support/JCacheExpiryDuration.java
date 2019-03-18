package org.springframework.cache.jcache.support;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

@FunctionalInterface
public interface JCacheExpiryDuration {
    Duration getDuration(Cache cache);
}
