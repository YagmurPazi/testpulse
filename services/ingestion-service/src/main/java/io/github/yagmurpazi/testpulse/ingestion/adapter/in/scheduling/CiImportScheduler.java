package io.github.yagmurpazi.testpulse.ingestion.adapter.in.scheduling;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportCiRunsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportSummary;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Imports the configured repositories' recent CI runs at a fixed delay. Polling, not webhooks: a webhook needs an
 * address GitHub can reach, which a local installation does not have (ADR-0012).
 */
public final class CiImportScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CiImportScheduler.class);

    private final ImportCiRunsUseCase importCiRuns;
    private final List<String> repositories;

    public CiImportScheduler(ImportCiRunsUseCase importCiRuns, List<String> repositories) {
        this.importCiRuns = Objects.requireNonNull(importCiRuns, "importCiRuns");
        this.repositories = List.copyOf(repositories);
    }

    /** One repository failing (GitHub down, no access) does not stop the others. */
    @Scheduled(initialDelayString = "${testpulse.importer.github.initial-delay:30s}",
            fixedDelayString = "${testpulse.importer.github.poll-interval:5m}")
    public void importAll() {
        for (String repository : repositories) {
            try {
                report(importCiRuns.importRuns(repository));
            } catch (RuntimeException e) {
                LOG.warn("Importing the CI runs of {} failed: {}", repository, e.getMessage());
            }
        }
    }

    private static void report(ImportSummary summary) {
        if (summary.imported() > 0) {
            LOG.info("Imported {} CI run(s) of {}", summary.imported(), summary.repository());
        }
        summary.failures().forEach(failure -> LOG.warn("Could not import {} of {}", failure, summary.repository()));
    }
}
