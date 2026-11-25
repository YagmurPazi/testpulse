package io.github.yagmurpazi.testpulse.ingestion.adapter.in.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestOutcome;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SurefireReportParserTest {

    private final SurefireReportParser parser = new SurefireReportParser();

    @Nested
    class RealReportsFromOwnCi {

        @Test
        void readsFailsafeReportWithPropertiesAndSystemOut() throws IOException {
            List<TestCaseResult> results = parse("/reports/ci/failsafe-ingestion-it.xml");

            assertThat(results)
                    .extracting(TestCaseResult::className, TestCaseResult::testName, TestCaseResult::outcome)
                    .containsExactlyInAnyOrder(
                            tuple("io.github.yagmurpazi.testpulse.ingestion.IngestionApplicationIT",
                                    "runsFlywayOnStartup", TestOutcome.PASSED),
                            tuple("io.github.yagmurpazi.testpulse.ingestion.IngestionApplicationIT",
                                    "connectsToPostgres", TestOutcome.PASSED));
            assertThat(results).allSatisfy(result -> {
                assertThat(result.failureMessage()).isNull();
                assertThat(result.rerunFailures()).isZero();
            });
        }

        @Test
        void readsSurefireReportWithDurations() throws IOException {
            List<TestCaseResult> results = parse("/reports/ci/surefire-architecture-rules.xml");

            assertThat(results).hasSize(5).allMatch(result -> result.outcome() == TestOutcome.PASSED);
            assertThat(results)
                    .filteredOn(result -> result.testName().equals("acceptsCompliantService"))
                    .singleElement()
                    .extracting(TestCaseResult::duration)
                    .isEqualTo(Duration.ofMillis(1008));
        }
    }

    @Nested
    class SurefireReruns {

        @Test
        void countsFlakyAttemptsOfTestThatPassedInTheEnd() throws IOException {
            TestCaseResult result = parse("/reports/samples/flaky-passed-on-rerun.xml").getFirst();

            assertThat(result.outcome()).isEqualTo(TestOutcome.PASSED);
            assertThat(result.rerunFailures()).isEqualTo(2);
            assertThat(result.passedOnRerun()).isTrue();
            assertThat(result.failureMessage()).isNull();
        }

        @Test
        void countsRerunsOfTestThatFailedOnEveryAttempt() throws IOException {
            TestCaseResult result = parse("/reports/samples/failed-after-reruns.xml").getFirst();

            assertThat(result.outcome()).isEqualTo(TestOutcome.FAILED);
            assertThat(result.rerunFailures()).isEqualTo(2);
            assertThat(result.failureMessage()).isEqualTo("expected: <400> but was: <500>");
        }
    }

    @Nested
    class AggregatedFormat {

        private List<TestCaseResult> results;

        AggregatedFormat() throws IOException {
            results = parse("/reports/samples/mixed-outcomes-testsuites.xml");
        }

        @Test
        void readsAllSuitesUnderTestsuitesRoot() {
            assertThat(results).extracting(TestCaseResult::testName).containsExactly(
                    "createsOrder", "handlesTimeout", "exportsPdf", "usesSuiteNameWhenClassnameIsMissing");
        }

        @Test
        void takesFirstLineOfElementTextWhenErrorHasNoMessageAttribute() {
            assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.ERROR);
            assertThat(results.get(1).failureMessage())
                    .isEqualTo("java.lang.IllegalStateException: pool exhausted");
        }

        @Test
        void marksSkippedTestsWithoutMessage() {
            assertThat(results.get(2).outcome()).isEqualTo(TestOutcome.SKIPPED);
            assertThat(results.get(2).failureMessage()).isNull();
        }

        @Test
        void fallsBackToSuiteNameWhenClassnameIsMissing() {
            assertThat(results.get(3).className()).isEqualTo("io.example.FallbackSuite");
            assertThat(results.get(3).duration()).isEqualTo(Duration.ZERO);
        }

        @Test
        void acceptsThousandsSeparatorInTime() {
            assertThat(results.get(0).duration()).isEqualTo(Duration.ofMillis(1_234_500));
        }
    }

    @Nested
    class FailureMessages {

        @Test
        void fallsBackToExceptionTypeWhenThereIsNoMessage() {
            TestCaseResult result = parseString("""
                    <testsuite name="S"><testcase name="t" classname="C">
                      <failure type="java.lang.AssertionError"/>
                    </testcase></testsuite>""").getFirst();

            assertThat(result.failureMessage()).isEqualTo("java.lang.AssertionError");
        }

        @Test
        void ignoresTextOfNestedElements() {
            TestCaseResult result = parseString("""
                    <testsuite name="S"><testcase name="t" classname="C">
                      <error type="java.io.IOException"><stackTrace>deep detail</stackTrace></error>
                    </testcase></testsuite>""").getFirst();

            assertThat(result.failureMessage()).isEqualTo("java.io.IOException");
        }

        @Test
        void readsFailureMessageFromCdataText() {
            TestCaseResult result = parseString("""
                    <testsuite name="S"><testcase name="t" classname="C">
                      <failure><![CDATA[
                    boom on first line
                    second line]]></failure>
                    </testcase></testsuite>""").getFirst();

            assertThat(result.failureMessage()).isEqualTo("boom on first line");
        }

        @Test
        void stopsCollectingVeryLongTextAtScanLimit() {
            String hugeLine = "x".repeat(10_000);
            TestCaseResult result = parseString(
                    "<testsuite name=\"S\"><testcase name=\"t\" classname=\"C\"><failure>" + hugeLine
                            + "</failure></testcase></testsuite>").getFirst();

            assertThat(result.failureMessage()).hasSize(4_096);
        }
    }

    @Nested
    class Security {

        @Test
        void rejectsExternalEntityInsteadOfReadingLocalFiles() {
            assertThatThrownBy(() -> parse("/reports/samples/xxe-external-entity.xml"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageNotContaining("root:");
        }

        @Test
        void rejectsEntityExpansion() {
            assertThatThrownBy(() -> parse("/reports/samples/billion-laughs.xml"))
                    .isInstanceOf(ReportParseException.class);
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void rejectsMalformedXml() {
            String unclosedTestCase = "<testsuite name=\"S\"><testcase name=\"t\" classname=\"C\"></testsuite>";

            assertThatThrownBy(() -> parseString(unclosedTestCase))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageStartingWith("Malformed XML report");
        }

        @Test
        void rejectsOtherXmlDocuments() {
            assertThatThrownBy(() -> parseString("<html><body/></html>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("root element is <html>");
        }

        @Test
        void rejectsEmptyInput() {
            assertThatThrownBy(() -> parseString("")).isInstanceOf(ReportParseException.class);
        }

        @Test
        void rejectsTestCaseWithoutName() {
            assertThatThrownBy(() -> parseString("<testsuite name=\"S\"><testcase classname=\"C\"/></testsuite>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("without name");
        }

        @Test
        void rejectsTestCaseWithoutClassOrSuiteName() {
            assertThatThrownBy(() -> parseString("<testsuite><testcase name=\"t\"/></testsuite>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("without classname");
        }

        @Test
        void returnsNoResultsForSuiteWithoutTestCases() {
            assertThat(parseString("<testsuite name=\"S\" tests=\"0\"/>")).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"0.0005, 1", "0.0004, 0", "1.5, 1500", "' 2 ', 2000", "'1,000', 1000000"})
    void convertsSecondsToMillisecondsRoundingHalfUp(String seconds, long millis) {
        assertThat(SurefireReportParser.parseDuration(seconds)).isEqualTo(Duration.ofMillis(millis));
    }

    @ParameterizedTest
    @CsvSource({"-1", "abc", "1e999999999"})
    void rejectsInvalidTimes(String seconds) {
        assertThatThrownBy(() -> SurefireReportParser.parseDuration(seconds))
                .isInstanceOf(ReportParseException.class);
    }

    private List<TestCaseResult> parse(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as("test resource %s", resource).isNotNull();
            return parser.parse(input);
        }
    }

    private List<TestCaseResult> parseString(String xml) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
