package io.github.yagmurpazi.testpulse.notification.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** At least one channel could not deliver a notification. Worth retrying: channel outages are usually brief. */
public class DeliveryFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> failedChannels;

    public DeliveryFailedException(UUID eventId, Map<String, RuntimeException> failures) {
        super("Delivery of " + eventId + " failed on " + failures.keySet() + ": " + describe(failures));
        this.failedChannels = List.copyOf(failures.keySet());
        failures.values().forEach(this::addSuppressed);
    }

    public List<String> failedChannels() {
        return failedChannels;
    }

    private static String describe(Map<String, RuntimeException> failures) {
        return failures.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue().getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
