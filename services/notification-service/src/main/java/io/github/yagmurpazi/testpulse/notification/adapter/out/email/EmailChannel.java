package io.github.yagmurpazi.testpulse.notification.adapter.out.email;

import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import java.util.List;
import java.util.Objects;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Plain text e-mail through SMTP (Mailpit locally). */
public final class EmailChannel implements NotificationChannel {

    static final String NAME = "email";

    private final JavaMailSender mailSender;
    private final String from;
    private final List<String> to;

    public EmailChannel(JavaMailSender mailSender, String from, List<String> to) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.from = Objects.requireNonNull(from, "from");
        this.to = List.copyOf(to);
        if (this.to.isEmpty()) {
            throw new IllegalArgumentException("the e-mail channel needs at least one recipient");
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    /** @throws MailException if the SMTP server did not accept the message */
    @Override
    public void send(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.toArray(String[]::new));
        message.setSubject("[TestPulse] " + notification.title());
        message.setText(body(notification));
        mailSender.send(message);
    }

    static String body(Notification notification) {
        StatusChange change = notification.change();
        return notification.summary() + "\n\n"
                + "Test:        " + change.className() + "." + change.testName() + "\n"
                + "Repository:  " + change.repository() + "\n"
                + "Status:      " + change.from() + " -> " + change.to() + (change.manual() ? " (manual)" : "") + "\n"
                + "Reason:      " + change.reason() + "\n"
                + "When:        " + change.occurredAt() + "\n\n"
                + notification.link() + "\n\n"
                + "-- \nTestPulse\n";
    }
}
