package com.tickethub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneOffset;
import java.util.TimeZone;

@SpringBootApplication
public class TicketHubApplication {

    public static void main(String[] args) {
        // Драйвер Postgres передає серверу зону JVM; застарілі назви (Europe/Kiev) новий tzdata відхиляє.
        // Усі часові поля — Instant/TIMESTAMPTZ, тож UTC нічого не змінює в даних.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        SpringApplication.run(TicketHubApplication.class, args);
    }
}
