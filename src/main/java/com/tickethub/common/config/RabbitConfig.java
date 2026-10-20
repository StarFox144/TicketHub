package com.tickethub.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Повідомлення — JSON. Тип для десеріалізації береться з параметра @RabbitListener-методу,
 * тож у повідомленні немає Java-імен класів і споживачем може бути будь-яка мова.
 * Топологію (exchange, черги, DLQ) оголошують модулі-споживачі.
 */
@Configuration
public class RabbitConfig {

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
