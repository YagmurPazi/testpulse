package io.github.yagmurpazi.testpulse.notification.adapter.out.email;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationFactory;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationKind;
import io.github.yagmurpazi.testpulse.notification.domain.TestStatus;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailChannelTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailChannel channel = new EmailChannel(mailSender, "testpulse@localhost",
            List.of("team@example.org", "qa@example.org"));
    private final Notification notification = new NotificationFactory(URI.create("http://localhost:3000"))
            .create(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY), NotificationKind.BECAME_FLAKY);

    @Test
    void sendsAPlainTextMailToEveryRecipient() {
        channel.send(notification);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getFrom()).isEqualTo("testpulse@localhost");
        assertThat(message.getTo()).containsExactly("team@example.org", "qa@example.org");
        assertThat(message.getSubject())
                .isEqualTo("[TestPulse] Flaky test: CheckoutServiceTest.appliesDiscount in acme/checkout");
        assertThat(message.getText()).isEqualTo(EmailChannel.body(notification));
    }

    @Test
    void bodyExplainsTheChangeAndLinksToTheTest() {
        assertThat(EmailChannel.body(notification))
                .startsWith(notification.summary())
                .contains("Test:        io.example.checkout.CheckoutServiceTest.appliesDiscount")
                .contains("Repository:  acme/checkout")
                .contains("Status:      SUSPICIOUS -> FLAKY\n")
                .contains("Reason:      Passed and failed on the same commit 9f2c4e1")
                .contains("When:        2026-11-25T12:00:00Z")
                .contains("http://localhost:3000/tests/ce1cbd14-a6b1-4100-8d48-2441ee64eb6f");
    }

    @Test
    void marksManualChanges() {
        Notification quarantined = new NotificationFactory(URI.create("http://localhost:3000"))
                .create(change(TestStatus.FLAKY, TestStatus.QUARANTINED), NotificationKind.QUARANTINED);

        assertThat(EmailChannel.body(quarantined)).contains("Status:      FLAKY -> QUARANTINED (manual)");
    }

    @Test
    void propagatesSmtpFailuresSoTheDeliveryIsRetried() {
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> channel.send(notification)).isInstanceOf(MailSendException.class);
    }

    @Test
    void needsARecipient() {
        assertThatThrownBy(() -> new EmailChannel(mailSender, "testpulse@localhost", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isNamedEmail() {
        assertThat(channel.name()).isEqualTo("email");
    }
}
