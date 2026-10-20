package com.tickethub.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** У тестах планувальник вимкнено: job-и викликаються напряму, щоб час і порядок були під контролем тесту. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tickethub.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
