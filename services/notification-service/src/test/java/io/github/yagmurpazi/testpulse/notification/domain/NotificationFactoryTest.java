package io.github.yagmurpazi.testpulse.notification.domain;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationFactoryTest {

    private final NotificationFactory factory = new NotificationFactory(URI.create("http://localhost:3000"));

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "BECAME_FLAKY, SUSPICIOUS,  FLAKY,       'Flaky test: ',               passes and fails without a code change",
        "QUARANTINED,  FLAKY,       QUARANTINED, 'Quarantined: ',              decided to ignore the result",
        "RELEASED,     QUARANTINED, SUSPICIOUS,  'Released from quarantine: ', observed again",
        "RECOVERED,    FLAKY,       STABLE,      'Recovered: ',                stable again"
    })
    void wordsEachKindForItsMeaning(NotificationKind kind, TestStatus from, TestStatus to, String titlePrefix,
            String summaryPart) {
        Notification notification = factory.create(change(from, to), kind);

        assertThat(notification.kind()).isEqualTo(kind);
        assertThat(notification.title())
                .isEqualTo(titlePrefix + "CheckoutServiceTest.appliesDiscount in acme/checkout");
        assertThat(notification.summary()).contains(summaryPart);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "http://localhost:3000/"})
    void linksToTheTestPageWhetherOrNotTheBaseUrlEndsWithASlash(String base) {
        Notification notification = new NotificationFactory(URI.create(base))
                .create(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY), NotificationKind.BECAME_FLAKY);

        assertThat(notification.link())
                .hasToString("http://localhost:3000/tests/ce1cbd14-a6b1-4100-8d48-2441ee64eb6f");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://ci.example.org/testpulse", "https://ci.example.org/testpulse/"})
    void keepsTheBasePathOfADashboardServedBelowTheRoot(String base) {
        Notification notification = new NotificationFactory(URI.create(base))
                .create(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY), NotificationKind.BECAME_FLAKY);

        assertThat(notification.link())
                .hasToString("https://ci.example.org/testpulse/tests/ce1cbd14-a6b1-4100-8d48-2441ee64eb6f");
    }

    @Test
    void keepsTheChangeForChannelsThatShowMoreDetail() {
        var change = change(TestStatus.SUSPICIOUS, TestStatus.FLAKY);

        assertThat(factory.create(change, NotificationKind.BECAME_FLAKY).change()).isSameAs(change);
    }
}
