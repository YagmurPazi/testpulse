package io.github.yagmurpazi.testpulse.analysis.adapter.in.web;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.TestNotFoundException;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.DailyTrend;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.RepositorySummary;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestDetails;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestQueries;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestSummary;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read API for the dashboard. Repositories are addressed as {@code /repositories/{owner}/{name}}. */
@RestController
@RequestMapping("/api/v1")
class TestQueryController {

    static final int LATEST_EXECUTIONS = 50;
    static final int MAX_TREND_DAYS = 365;

    private final TestQueries queries;
    private final Clock clock;

    TestQueryController(TestQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping("/repositories")
    List<RepositorySummary> repositories() {
        return queries.repositories();
    }

    @GetMapping("/repositories/{owner}/{name}/tests")
    List<TestSummary> tests(@PathVariable String owner, @PathVariable String name,
            @RequestParam Optional<TestStatus> status) {
        return queries.tests(owner + "/" + name, status);
    }

    @GetMapping("/repositories/{owner}/{name}/trend")
    List<DailyTrend> trend(@PathVariable String owner, @PathVariable String name,
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > MAX_TREND_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_TREND_DAYS);
        }
        return queries.trend(owner + "/" + name, clock.instant().minus(Duration.ofDays(days)));
    }

    @GetMapping("/tests/{testId}")
    TestDetails test(@PathVariable UUID testId) {
        return queries.test(testId, LATEST_EXECUTIONS).orElseThrow(() -> new TestNotFoundException(testId));
    }
}
