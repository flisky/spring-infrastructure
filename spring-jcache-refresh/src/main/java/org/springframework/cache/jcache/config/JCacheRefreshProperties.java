package org.springframework.cache.jcache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties("spring.cache.refresh")
public class JCacheRefreshProperties {
    private double expiryFactor = 0.95;
    private Duration eternalExpiry = Duration.ZERO;
    private Duration executionJitter = Duration.ZERO;
    private Duration executionTimeout = Duration.ZERO;
}
