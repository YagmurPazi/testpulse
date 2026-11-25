package io.github.yagmurpazi.testpulse.notification;

import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** The captured e-mails, through Mailpit's API. */
public final class Mailpit {

    private final RestClient client;

    public Mailpit(String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public List<String> subjects() {
        JsonNode messages = client.get().uri("/api/v1/messages").retrieve().body(JsonNode.class);
        return messages == null ? List.of()
                : messages.path("messages").valueStream().map(message -> message.path("Subject").asString()).toList();
    }

    public void deleteAll() {
        client.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
    }
}
