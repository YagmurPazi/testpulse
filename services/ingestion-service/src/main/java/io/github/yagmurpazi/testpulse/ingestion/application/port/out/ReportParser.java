package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import java.io.InputStream;
import java.util.List;

/**
 * Reads test results from a report file. A driven port, so every driving adapter (web upload, GitHub importer)
 * shares one parser without depending on another adapter.
 */
public interface ReportParser {

    /**
     * Parses one report. The caller owns and closes the stream.
     *
     * @throws InvalidReportException if the content is not a readable report
     */
    List<TestCaseResult> parse(InputStream report);
}
