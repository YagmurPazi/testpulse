package io.github.yagmurpazi.testpulse.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.yagmurpazi.testpulse.notification.application.port.in.NotifyStatusChangeUseCase;
import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

/** Which channels exist for which configuration. */
class NotificationConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfiguration.class)
            .withBean(DeliveryLog.class, () -> mock(DeliveryLog.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("testpulse.notification.dashboard-url=http://localhost:3000",
                    "testpulse.notification.max-age=24h",
                    "testpulse.notification.email.from=testpulse@localhost",
                    "testpulse.notification.email.to=team@localhost");

    @Test
    void runsWithoutAnyChannel() {
        context.withPropertyValues("testpulse.notification.email.enabled=false",
                "testpulse.notification.slack.webhook-url=")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(NotifyStatusChangeUseCase.class);
                    assertThat(ctx).doesNotHaveBean(NotificationChannel.class);
                });
    }

    @Test
    void createsTheEmailChannelWhenEnabled() {
        context.withPropertyValues("testpulse.notification.email.enabled=true")
                .run(ctx -> assertThat(ctx.getBeansOfType(NotificationChannel.class).values())
                        .extracting(NotificationChannel::name).containsExactly("email"));
    }

    @Test
    void createsTheSlackChannelWhenAWebhookIsSet() {
        context.withPropertyValues("testpulse.notification.email.enabled=false",
                "testpulse.notification.slack.webhook-url=https://hooks.slack.com/services/T000/B000/secret")
                .run(ctx -> assertThat(ctx.getBeansOfType(NotificationChannel.class).values())
                        .extracting(NotificationChannel::name).containsExactly("slack"));
    }

    @Test
    void treatsABlankWebhookAsNoSlack() {
        context.withPropertyValues("testpulse.notification.email.enabled=false",
                "testpulse.notification.slack.webhook-url=   ")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(NotificationChannel.class));
    }
}
