package org.springframework.cache.jcache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties("spring.cache.refresh")
public class JCacheRefreshProperties {
    double expiryFactor = 0.95;
    Duration eternalExpiry = Duration.ZERO;
    Duration executionTimeout = Duration.ZERO;
}
