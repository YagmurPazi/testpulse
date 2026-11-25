package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import java.util.List;

/** A CI system that keeps test reports of its runs (GitHub Actions artifacts). */
public interface CiRunSource {

    /** The most recent completed runs of the repository, in any order. */
    List<CiRun> recentCompletedRuns(String repository);

    /** The JUnit XML reports the run kept; empty if it kept none or they have expired. */
    List<ReportFile> downloadReports(CiRun run);
}
