package com.tickethub.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Postgres, Redis, RabbitMQ і Mailpit — по одному контейнеру на весь прогін тестів (singleton container pattern):
 * Spring кешує контекст між тестовими класами, і контейнери не перезапускаються.
 * Дані в Redis і листи в Mailpit спільні для всіх класів, тому тести використовують унікальні id/email/IP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    /** Справжній SMTP-сервер з HTTP API: тест бачить лист так само, як його побачить користувач. */
    protected static final GenericContainer<?> MAILPIT =
            new GenericContainer<>("axllent/mailpit:v1.31.5").withExposedPorts(1025, 8025);

    static {
        Startables.deepStart(POSTGRES, REDIS, RABBIT, MAILPIT).join();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    protected static Mailpit mailpit() {
        return new Mailpit("http://%s:%d".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025)));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestData testData;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Імітація збою Redis: процес заморожено, з'єднання відкрите, команди чекають до таймауту. */
    protected static void pauseRedis() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    }

    protected static void unpauseRedis() {
        REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
}
