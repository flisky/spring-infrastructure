package org.springframework.cache.jcache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.jcache.support.JCacheExpiryDuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnClass(Caffeine.class)
public class CaffeineJCacheRefreshConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JCacheExpiryDuration expiryDuration() {
        return new CaffeineJCacheExpiryDuration();
    }

    static class CaffeineJCacheExpiryDuration implements JCacheExpiryDuration {
        @Override
        public Duration getDuration(Cache cache) {
            CaffeineConfiguration config = (CaffeineConfiguration) cache.getConfiguration(CaffeineConfiguration.class);
            if (config.getExpireAfterAccess().isPresent()) {
                return toDuration(config.getExpireAfterAccess().getAsLong());
            }
            if (config.getExpireAfterWrite().isPresent()) {
                return toDuration(config.getExpireAfterWrite().getAsLong());
            }
            ExpiryPolicy expiryPolicy = (ExpiryPolicy) config.getExpiryPolicyFactory().create();
            return expiryPolicy.getExpiryForCreation();
        }

        private Duration toDuration(long nano) {
            return new Duration(TimeUnit.MILLISECONDS, TimeUnit.NANOSECONDS.toMillis(nano));
        }
    }
}
