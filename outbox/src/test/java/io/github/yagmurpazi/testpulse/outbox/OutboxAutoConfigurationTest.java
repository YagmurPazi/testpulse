package io.github.yagmurpazi.testpulse.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class OutboxAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(KafkaOperations.class, () -> mock(KafkaOperations.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void registersWriterRelayAndSchedulerByDefault() {
        context.run(app -> {
            assertThat(app).hasSingleBean(OutboxWriter.class);
            assertThat(app).hasSingleBean(OutboxRelay.class);
            assertThat(app).hasSingleBean(OutboxAutoConfiguration.Scheduling.class);
        });
    }

    @Test
    void usesDocumentedDefaults() {
        context.run(app -> {
            OutboxProperties.Relay relay = app.getBean(OutboxProperties.class).relay();
            assertThat(relay.enabled()).isTrue();
            assertThat(relay.pollInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(relay.batchSize()).isEqualTo(100);
            assertThat(relay.sendTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void doesNotScheduleTheRelayWhenDisabled() {
        context.withPropertyValues("testpulse.outbox.relay.enabled=false").run(app -> {
            assertThat(app).hasSingleBean(OutboxRelay.class);
            assertThat(app).doesNotHaveBean(OutboxAutoConfiguration.Scheduling.class);
        });
    }

    @Test
    void bindsCustomSettings() {
        context.withPropertyValues("testpulse.outbox.relay.batch-size=7", "testpulse.outbox.relay.send-timeout=3s")
                .run(app -> {
                    OutboxProperties.Relay relay = app.getBean(OutboxProperties.class).relay();
                    assertThat(relay.batchSize()).isEqualTo(7);
                    assertThat(relay.sendTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }
}
