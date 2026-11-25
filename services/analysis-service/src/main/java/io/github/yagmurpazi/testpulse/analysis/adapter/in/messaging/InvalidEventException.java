package io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging;

/**
 * An event that can never be processed: unreadable JSON, an unsupported schema version or invalid content.
 * Retrying cannot help, so the record is logged and skipped instead of blocking its partition.
 */
public class InvalidEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidEventException(String message) {
        super(message);
    }
}
