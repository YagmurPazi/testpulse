package io.github.yagmurpazi.testpulse.notification.adapter.in.messaging;

/** A record that can never be processed: retrying it would only block its partition. */
public class InvalidEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
