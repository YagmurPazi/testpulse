package io.github.yagmurpazi.testpulse.ingestion.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportCiRunsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportSummary;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CiImportSchedulerTest {

    @Test
    void importsEveryRepositoryEvenWhenOneFails() {
        List<String> imported = new ArrayList<>();
        ImportCiRunsUseCase useCase = repository -> {
            imported.add(repository);
            if (repository.equals("acme/private")) {
                throw new IllegalStateException("401 Unauthorized");
            }
            return new ImportSummary(repository, 1, 0, 0, List.of("run 1 attempt 1: broken"));
        };

        new CiImportScheduler(useCase, List.of("acme/private", "YagmurPazi/testpulse")).importAll();

        assertThat(imported).containsExactly("acme/private", "YagmurPazi/testpulse");
    }
}
