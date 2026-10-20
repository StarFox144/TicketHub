package com.tickethub.booking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {

    /** У тестах планувальник вимкнено: job викликають напряму, щоб час був під контролем тесту. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "tickethub.booking.expiration-job.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
