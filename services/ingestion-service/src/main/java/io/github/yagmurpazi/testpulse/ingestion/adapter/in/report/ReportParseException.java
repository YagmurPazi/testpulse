package io.github.yagmurpazi.testpulse.ingestion.adapter.in.report;

/** The uploaded content is not a readable JUnit XML report. */
public class ReportParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReportParseException(String message) {
        super(message);
    }

    public ReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
