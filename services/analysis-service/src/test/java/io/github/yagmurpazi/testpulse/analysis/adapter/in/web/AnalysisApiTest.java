package io.github.yagmurpazi.testpulse.analysis.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.TestNotFoundException;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.RepositorySummary;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestQueries;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestSummary;
import io.github.yagmurpazi.testpulse.analysis.domain.InvalidStatusTransitionException;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** HTTP contract of the read and status APIs; the application is mocked. */
@WebMvcTest({TestQueryController.class, TestStatusController.class})
@Import(AnalysisApiTest.FixedClock.class)
class AnalysisApiTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final UUID TEST_ID = UUID.fromString("3f2e1d0c-9b8a-4766-a554-433221100fed");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TestQueries queries;

    @MockitoBean
    private ChangeTestStatusUseCase changeTestStatus;

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void listsRepositories() {
        given(queries.repositories()).willReturn(List.of(new RepositorySummary("acme/checkout", 4, 3, 0, 1, 0, NOW)));

        MvcTestResult result = mvc.get().uri("/api/v1/repositories").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].repository").isEqualTo("acme/checkout");
        assertThat(result).bodyJson().extractingPath("$[0].flaky").isEqualTo(1);
    }

    @Test
    void filtersTestsOfARepositoryByStatus() {
        given(queries.tests("acme/checkout", Optional.of(TestStatus.FLAKY))).willReturn(List.of(new TestSummary(
                TEST_ID, "io.example.CheckoutTest", "appliesDiscount", TestStatus.FLAKY, NOW, "evidence", 3, 1)));

        MvcTestResult result = mvc.get().uri("/api/v1/repositories/acme/checkout/tests?status=FLAKY").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].testId").isEqualTo(TEST_ID.toString());
        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("FLAKY");
    }

    @Test
    void listsAllTestsWithoutStatusFilter() {
        mvc.get().uri("/api/v1/repositories/acme/checkout/tests").exchange();

        verify(queries).tests("acme/checkout", Optional.empty());
    }

    @Test
    void rejectsUnknownStatus() {
        MvcTestResult result = mvc.get().uri("/api/v1/repositories/acme/checkout/tests?status=BROKEN").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void computesTheTrendWindowFromTheClock() {
        mvc.get().uri("/api/v1/repositories/acme/checkout/trend?days=7").exchange();

        verify(queries).trend("acme/checkout", NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void rejectsTrendWindowsOutsideTheAllowedRange() {
        assertThat(mvc.get().uri("/api/v1/repositories/acme/checkout/trend?days=0").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/repositories/acme/checkout/trend?days=366").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/repositories/acme/checkout/trend?days=365").exchange()).hasStatusOk();
    }

    @Test
    void answersNotFoundForUnknownTest() {
        given(queries.test(any(), anyInt())).willReturn(Optional.empty());

        MvcTestResult result = mvc.get().uri("/api/v1/tests/" + TEST_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Test not found");
    }

    @Test
    void quarantinesATest() {
        given(changeTestStatus.quarantine(TEST_ID)).willReturn(new StatusChange(TEST_ID,
                new TestKey("acme/checkout", "C", "t"), TestStatus.FLAKY, TestStatus.QUARANTINED,
                "Quarantined by the team", NOW, null));

        MvcTestResult result = mvc.post().uri("/api/v1/tests/" + TEST_ID + "/quarantine").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.to").isEqualTo("QUARANTINED");
        assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("Quarantined by the team");
    }

    @Test
    void answersConflictWhenTheStatusDoesNotAllowTheAction() {
        given(changeTestStatus.release(TEST_ID))
                .willThrow(new InvalidStatusTransitionException(TestStatus.STABLE, "release"));

        MvcTestResult result = mvc.post().uri("/api/v1/tests/" + TEST_ID + "/release").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("STABLE");
    }

    @Test
    void answersNotFoundWhenQuarantiningAnUnknownTest() {
        given(changeTestStatus.quarantine(TEST_ID)).willThrow(new TestNotFoundException(TEST_ID));

        assertThat(mvc.post().uri("/api/v1/tests/" + TEST_ID + "/quarantine").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
