package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's clock, as a bean.
 *
 * <p>So that anything deciding "is it a reasonable hour where this user is"
 * can be tested at 3am without waiting until 3am.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        // UTC rather than the server's zone: every conversion in the codebase
        // goes through the user's own zone anyway, and a server that moves
        // between regions should not change behaviour.
        return Clock.systemUTC();
    }
}
