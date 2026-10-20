package com.tickethub.common.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Кеш у Redis — лише прискорювач: PostgreSQL лишається єдиним джерелом правди.
 * Тому помилка Redis не ламає запит (LoggingCacheErrorHandler), а читання просто йде в БД.
 * Налаштування конкретних кешів (TTL, серіалізація) живуть у модулях, яким ці кеші належать.
 * <p>
 * transactionAware() навмисно не вмикаємо: він відкладає запис у кеш до afterCommit, де
 * CacheErrorHandler уже не діє, — і таймаут Redis перетворювався на 500 для вже закоміченого замовлення.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
