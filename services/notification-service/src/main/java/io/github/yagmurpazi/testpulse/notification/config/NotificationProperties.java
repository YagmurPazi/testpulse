package io.github.yagmurpazi.testpulse.notification.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code testpulse.notification.*}.
 *
 * @param dashboardUrl where links in notifications point to
 * @param maxAge       changes older than this are not notified (ADR-0011)
 */
@ConfigurationProperties("testpulse.notification")
record NotificationProperties(URI dashboardUrl, Duration maxAge, Email email, Slack slack) {

    /** @param enabled e-mail needs an SMTP server ({@code spring.mail.*}) and at least one recipient */
    record Email(boolean enabled, String from, List<String> to) {
    }

    /** @param webhookUrl Slack incoming webhook; Slack is disabled while it is empty */
    record Slack(String webhookUrl) {
    }
}
