package io.github.yagmurpazi.testpulse.notification.domain;

import java.net.URI;
import java.util.Objects;

/**
 * Creates the {@link Notification} for a kind of change (Factory): callers ask for "a notification about this
 * change" and get the right title and wording, without knowing the variants.
 */
public final class NotificationFactory {

    private final URI dashboardUrl;

    /** @param dashboardUrl base URL of the dashboard, e.g. {@code http://localhost:3000/} */
    public NotificationFactory(URI dashboardUrl) {
        Objects.requireNonNull(dashboardUrl, "dashboardUrl");
        String base = dashboardUrl.toString();
        this.dashboardUrl = URI.create(base.endsWith("/") ? base : base + "/");
    }

    public Notification create(StatusChange change, NotificationKind kind) {
        String test = change.testLabel();
        String where = " in " + change.repository();
        URI link = dashboardUrl.resolve("tests/" + change.testId());
        return switch (kind) {
            case BECAME_FLAKY -> new Notification(change, kind, "Flaky test: " + test + where,
                    test + " passes and fails without a code change, so its failures are probably not caused by "
                            + "the change under test.", link);
            case QUARANTINED -> new Notification(change, kind, "Quarantined: " + test + where,
                    "The team decided to ignore the result of " + test + " while it is being fixed.", link);
            case RELEASED -> new Notification(change, kind, "Released from quarantine: " + test + where,
                    test + " is observed again and becomes flaky only on new evidence.", link);
            case RECOVERED -> new Notification(change, kind, "Recovered: " + test + where,
                    test + " has passed consistently and is stable again.", link);
        };
    }
}
