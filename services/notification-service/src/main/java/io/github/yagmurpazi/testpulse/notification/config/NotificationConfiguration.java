package io.github.yagmurpazi.testpulse.notification.config;

import io.github.yagmurpazi.testpulse.notification.adapter.out.email.EmailChannel;
import io.github.yagmurpazi.testpulse.notification.adapter.out.slack.SlackChannel;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotifyStatusChangeUseCase;
import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import io.github.yagmurpazi.testpulse.notification.application.service.NotifyStatusChangeService;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationFactory;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the use case to the channels that are configured. A channel without configuration is simply absent,
 * so the service runs (and records nothing) even with no channel at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Bean
    NotifyStatusChangeUseCase notifyStatusChangeUseCase(NotificationProperties properties,
            ObjectProvider<NotificationChannel> channelProvider, DeliveryLog deliveryLog, Clock clock) {
        List<NotificationChannel> channels = channelProvider.orderedStream().toList();
        if (channels.isEmpty()) {
            LOG.warn("No notification channel is configured: status changes are not sent anywhere");
        } else {
            LOG.info("Notification channels: {}", channels.stream().map(NotificationChannel::name).toList());
        }
        return new NotifyStatusChangeService(new NotificationPolicy(properties.maxAge()),
                new NotificationFactory(properties.dashboardUrl()), channels, deliveryLog, clock);
    }

    @Bean
    @ConditionalOnBooleanProperty("testpulse.notification.email.enabled")
    NotificationChannel emailChannel(JavaMailSender mailSender, NotificationProperties properties) {
        return new EmailChannel(mailSender, properties.email().from(), properties.email().to());
    }

    @Bean
    @ConditionalOnExpression("!'${testpulse.notification.slack.webhook-url:}'.isBlank()")
    NotificationChannel slackChannel(NotificationProperties properties, JsonMapper jsonMapper) {
        // Bounded timeouts: a hanging webhook must fail the delivery, not stall the consumer.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new SlackChannel(RestClient.builder().requestFactory(requestFactory).build(),
                URI.create(properties.slack().webhookUrl()), jsonMapper);
    }
}
