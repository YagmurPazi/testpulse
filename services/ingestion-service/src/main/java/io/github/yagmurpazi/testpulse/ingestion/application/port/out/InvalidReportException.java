package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

/** The content of an uploaded report cannot be read as a test report. The client must fix the input. */
public class InvalidReportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidReportException(String message) {
        super(message);
    }

    public InvalidReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
