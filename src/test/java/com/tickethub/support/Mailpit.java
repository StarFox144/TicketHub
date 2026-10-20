package com.tickethub.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** Мінімальний клієнт HTTP API Mailpit: які листи отримала адреса. */
public class Mailpit {

    public record Message(String id, String subject, int attachments) {
    }

    private final RestClient client;

    Mailpit(String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public List<Message> messagesTo(String email) {
        var body = client.get()
                .uri(uri -> uri.path("/api/v1/search").queryParam("query", "to:\"" + email + "\"").build())
                .retrieve()
                .body(JsonNode.class);
        List<Message> messages = new ArrayList<>();
        for (var message : body.path("messages")) {
            messages.add(new Message(message.path("ID").asText(), message.path("Subject").asText(),
                    message.path("Attachments").asInt()));
        }
        return messages;
    }

    /** Повний текст листа (HTML) — перевірити, що шаблон заповнено. */
    public String html(String messageId) {
        return client.get().uri("/api/v1/message/{id}", messageId).retrieve().body(JsonNode.class)
                .path("HTML").asText();
    }
}
