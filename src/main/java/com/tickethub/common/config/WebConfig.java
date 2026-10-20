package com.tickethub.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import java.time.Clock;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {

    /** Інжектимо Clock, щоб у тестах керувати часом (резерви, протухання). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
