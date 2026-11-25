package io.github.yagmurpazi.testpulse.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Contract tests for the {@link TestStatusChanged} wire format, pinned by its v1 golden file. */
class TestStatusChangedContractTest {

    private static final String V1_FIXTURE = "/contracts/test-status-changed-v1.json";
    private static final UUID RUN_ID = UUID.fromString("0b8e7d6c-5a4f-4e3d-8c2b-1a0f9e8d7c6b");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsPublishedV1Format() throws IOException {
        assertThat(mapper.readValue(fixture(), TestStatusChanged.class)).isEqualTo(sampleEvent(RUN_ID));
    }

    @Test
    void writesPublishedV1Format() throws IOException {
        JsonNode written = mapper.readTree(mapper.writeValueAsString(sampleEvent(RUN_ID)));

        assertThat(written).isEqualTo(mapper.readTree(fixture()));
    }

    @Test
    void ignoresUnknownFields() throws IOException {
        ObjectNode withNewField = (ObjectNode) mapper.readTree(fixture());
        withNewField.put("addedInALaterRelease", 42);

        assertThat(mapper.treeToValue(withNewField, TestStatusChanged.class)).isEqualTo(sampleEvent(RUN_ID));
    }

    @Test
    void allowsManualChangesWithoutTriggeringRun() {
        TestStatusChanged manual = sampleEvent(null);

        assertThat(mapper.readValue(mapper.writeValueAsString(manual), TestStatusChanged.class)).isEqualTo(manual);
    }

    @Test
    void rejectsChangeToTheSameStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TestStatusChanged(UUID.randomUUID(), 1,
                Instant.now(), UUID.randomUUID(), "acme/checkout", "C", "t", TestStatus.FLAKY, TestStatus.FLAKY,
                "no change", null));
    }

    private static TestStatusChanged sampleEvent(UUID triggeringRunId) {
        return new TestStatusChanged(
                UUID.fromString("7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"),
                TestStatusChanged.SCHEMA_VERSION,
                Instant.parse("2026-11-26T09:30:00Z"),
                UUID.fromString("3f2e1d0c-9b8a-4766-a554-433221100fed"),
                "acme/checkout",
                "io.example.checkout.CheckoutServiceTest",
                "appliesDiscount",
                TestStatus.SUSPICIOUS,
                TestStatus.FLAKY,
                "Passed and failed on commit 9f2c4e1",
                triggeringRunId);
    }

    private static InputStream fixture() {
        return TestStatusChangedContractTest.class.getResourceAsStream(V1_FIXTURE);
    }
}
