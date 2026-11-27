package io.github.yagmurpazi.testpulse.ingestion.application.service;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.ReportParser;
import io.github.yagmurpazi.testpulse.ingestion.domain.CommitSha;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Turns uploaded report files into one test run.
 *
 * <p>Parsing happens before, and outside of, the ingestion transaction, so slow or invalid uploads never hold a
 * database connection.
 */
public final class UploadReportsService implements UploadReportsUseCase {

    static final String CONTENT_HASH_PREFIX = "sha256:";

    private final ReportParser parser;
    private final IngestTestRunUseCase ingestTestRun;

    public UploadReportsService(ReportParser parser, IngestTestRunUseCase ingestTestRun) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.ingestTestRun = Objects.requireNonNull(ingestTestRun, "ingestTestRun");
    }

    @Override
    public IngestionResult upload(UploadReportsCommand command) {
        if (command.reports().isEmpty()) {
            throw new IllegalArgumentException("at least one report file is required");
        }
        RunIdentity identity = new RunIdentity(command.repository(), externalRunId(command), command.attempt());
        CommitSha commitSha = new CommitSha(command.commitSha());

        List<TestCaseResult> results = new ArrayList<>();
        for (ReportFile report : command.reports()) {
            results.addAll(parse(report));
        }

        return ingestTestRun.ingest(new IngestTestRunCommand(identity, commitSha, command.branch(), results));
    }

    private List<TestCaseResult> parse(ReportFile report) {
        try (InputStream content = report.open()) {
            return parser.parse(content);
        } catch (InvalidReportException e) {
            throw new InvalidReportException(report.name() + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String externalRunId(UploadReportsCommand command) {
        String provided = command.externalRunId();
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        return CONTENT_HASH_PREFIX + contentHash(command.reports());
    }

    /**
     * SHA-256 over the per-file hashes in sorted order: the same set of files gives the same identity regardless of
     * the order in which they were uploaded.
     */
    static String contentHash(List<ReportFile> reports) {
        List<String> fileHashes = reports.stream().map(report -> sha256(report.content())).sorted().toList();
        return sha256(String.join("\n", fileHashes).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
