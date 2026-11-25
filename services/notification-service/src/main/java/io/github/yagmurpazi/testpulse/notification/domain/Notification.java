package io.github.yagmurpazi.testpulse.notification.domain;

import java.net.URI;
import java.util.Objects;

/**
 * A message about one status change, independent of the channel it is sent through. Channels decide the format
 * (plain text e-mail, Slack markup) but not the content.
 *
 * @param title   one line, used as e-mail subject and Slack headline
 * @param summary what happened and what it means, one or two sentences
 * @param link    the test's page on the dashboard
 */
public record Notification(StatusChange change, NotificationKind kind, String title, String summary, URI link) {

    public Notification {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(link, "link");
    }
}
