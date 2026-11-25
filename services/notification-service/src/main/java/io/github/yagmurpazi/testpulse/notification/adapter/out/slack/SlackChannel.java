package io.github.yagmurpazi.testpulse.notification.adapter.out.slack;

import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A message through a Slack incoming webhook. The webhook URL is a secret: it alone allows posting to the channel,
 * so it comes from the environment and is never logged.
 */
public final class SlackChannel implements NotificationChannel {

    static final String NAME = "slack";

    private final RestClient restClient;
    private final URI webhookUrl;
    private final JsonMapper jsonMapper;

    public SlackChannel(RestClient restClient, URI webhookUrl, JsonMapper jsonMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public String name() {
        return NAME;
    }

    /** @throws RestClientException if Slack did not accept the message */
    @Override
    public void send(Notification notification) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(payload(notification)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Block Kit message; {@code text} is the fallback for notifications and clients without blocks. */
    static Map<String, Object> payload(Notification notification) {
        StatusChange change = notification.change();
        String headline = "*<" + notification.link() + "|" + escape(notification.title()) + ">*";
        String details = escape(change.repository() + " · " + change.from() + " → " + change.to()
                + (change.manual() ? " (manual)" : ""));
        return Map.of(
                "text", notification.title(),
                "blocks", List.of(
                        Map.of("type", "section", "text", Map.of("type", "mrkdwn",
                                "text", headline + "\n" + escape(notification.summary()) + "\n>"
                                        + escape(change.reason()))),
                        Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", details)))));
    }

    /** Slack's mrkdwn treats these three characters as markup. */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
