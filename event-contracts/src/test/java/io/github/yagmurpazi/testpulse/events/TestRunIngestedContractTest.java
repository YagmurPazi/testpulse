package io.github.yagmurpazi.testpulse.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contract tests for the {@link TestRunIngested} wire format.
 *
 * <p>The golden file {@code contracts/test-run-ingested-v1.json} is the v1 format as first published;
 * {@code contracts/test-run-ingested-v1-parts.json} is v1 with the optional {@code part} and {@code parts} fields added
 * later. If one of these tests fails after a change, the change breaks consumers: keep the old format or introduce a
 * new schema version.
 */
class TestRunIngestedContractTest {

    private static final String V1_FIXTURE = "/contracts/test-run-ingested-v1.json";
    private static final String V1_PARTS_FIXTURE = "/contracts/test-run-ingested-v1-parts.json";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsPublishedV1Format() throws IOException {
        TestRunIngested event = mapper.readValue(fixture(), TestRunIngested.class);

        assertThat(event).isEqualTo(sampleEvent());
    }

    @Test
    void readsAnEventWrittenBeforePartsExistedAsTheWholeRun() throws IOException {
        TestRunIngested event = mapper.readValue(fixture(), TestRunIngested.class);

        assertThat(event.part()).isEqualTo(1);
        assertThat(event.parts()).isEqualTo(1);
    }

    @Test
    void readsPartOfASplitRun() throws IOException {
        TestRunIngested event = mapper.readValue(fixture(V1_PARTS_FIXTURE), TestRunIngested.class);

        assertThat(event).isEqualTo(secondOfThreeParts());
    }

    @Test
    void writesV1FormatWithParts() throws IOException {
        // Parse the written JSON instead of using valueToTree, so both trees use the same numeric node types.
        JsonNode written = mapper.readTree(mapper.writeValueAsString(secondOfThreeParts()));

        assertThat(written).isEqualTo(mapper.readTree(fixture(V1_PARTS_FIXTURE)));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "2, 1", "-1, 2", "1, 0"})
    void rejectsAPartOutsideTheParts(int part, int parts) {
        TestRunIngested whole = sampleEvent();

        assertThatThrownBy(() -> new TestRunIngested(whole.eventId(), whole.schemaVersion(), whole.occurredAt(),
                whole.runId(), whole.repository(), whole.commitSha(), whole.branch(), whole.externalRunId(),
                whole.attempt(), whole.results(), part, parts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part must be between 1 and parts");
    }

    @Test
    void ignoresUnknownFieldsSoProducersCanAddOptionalFields() throws IOException {
        ObjectNode withNewField = (ObjectNode) mapper.readTree(fixture());
        withNewField.put("addedInALaterRelease", "value");

        TestRunIngested event = mapper.treeToValue(withNewField, TestRunIngested.class);

        assertThat(event).isEqualTo(sampleEvent());
    }

    @Test
    void roundTripsThroughJson() {
        TestRunIngested event = sampleEvent();

        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readValue(json, TestRunIngested.class)).isEqualTo(event);
    }

    @Test
    void defensivelyCopiesResults() {
        List<TestCaseResult> results = new ArrayList<>(sampleEvent().results());
        TestRunIngested event = withResults(results);

        results.clear();

        assertThat(event.results()).hasSize(2);
        assertThatThrownBy(() -> event.results().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAttemptBelowOne() {
        TestRunIngested valid = sampleEvent();

        assertThatThrownBy(() -> new TestRunIngested(valid.eventId(), valid.schemaVersion(), valid.occurredAt(),
                valid.runId(), valid.repository(), valid.commitSha(), valid.branch(), valid.externalRunId(), 0,
                valid.results()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }

    @Test
    void rejectsFailureMessageAboveLimit() {
        String tooLong = "x".repeat(TestCaseResult.MAX_FAILURE_MESSAGE_LENGTH + 1);

        assertThatThrownBy(() -> new TestCaseResult("C", "t", TestOutcome.FAILED, 1, 0, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TestRunIngested sampleEvent() {
        return withResults(List.of(
                new TestCaseResult("io.github.example.CheckoutServiceTest", "appliesDiscount",
                        TestOutcome.PASSED, 42, 1, null),
                new TestCaseResult("io.github.example.CheckoutServiceTest", "rejectsExpiredCoupon",
                        TestOutcome.FAILED, 7, 0, "expected: <400> but was: <500>")));
    }

    private static TestRunIngested withResults(List<TestCaseResult> results) {
        return new TestRunIngested(
                UUID.fromString("5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e6f"),
                TestRunIngested.SCHEMA_VERSION,
                Instant.parse("2026-11-25T12:00:00Z"),
                UUID.fromString("0b8e7d6c-5a4f-4e3d-8c2b-1a0f9e8d7c6b"),
                "YagmurPazi/testpulse",
                "3be2117c0ffee0000000000000000000000000ab",
                "main",
                "45619835962",
                2,
                results);
    }

    private static TestRunIngested secondOfThreeParts() {
        TestRunIngested whole = sampleEvent();
        return new TestRunIngested(whole.eventId(), whole.schemaVersion(), whole.occurredAt(), whole.runId(),
                whole.repository(), whole.commitSha(), whole.branch(), whole.externalRunId(), whole.attempt(),
                whole.results(), 2, 3);
    }

    private static InputStream fixture() {
        return fixture(V1_FIXTURE);
    }

    private static InputStream fixture(String resource) {
        return TestRunIngestedContractTest.class.getResourceAsStream(resource);
    }
}
