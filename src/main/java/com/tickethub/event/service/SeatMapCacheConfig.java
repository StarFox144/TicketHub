package com.tickethub.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.event.api.dto.SeatView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.List;

@Configuration
public class SeatMapCacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer seatMapCache(ObjectMapper objectMapper,
                                                    @Value("${tickethub.cache.seat-map-ttl}") Duration ttl) {
        // типізований серіалізатор: records фінальні, тож універсальний JSON з type-info тут не підходить
        var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, SeatView.class);
        var serializer = new Jackson2JsonRedisSerializer<List<SeatView>>(objectMapper, listType);
        return builder -> builder.withCacheConfiguration(SeatMapService.CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(ttl)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
