package io.github.yagmurpazi.testpulse.notification.adapter.out.slack;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationFactory;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationKind;
import io.github.yagmurpazi.testpulse.notification.domain.TestStatus;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

class SlackChannelTest {

    private static final URI WEBHOOK = URI.create("https://hooks.slack.com/services/T000/B000/secret");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer slack = MockRestServiceServer.bindTo(builder).build();
    private final SlackChannel channel = new SlackChannel(builder.build(), WEBHOOK, JsonMapper.builder().build());
    private final Notification notification = new NotificationFactory(URI.create("http://localhost:3000"))
            .create(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY), NotificationKind.BECAME_FLAKY);

    @Test
    void postsABlockKitMessageToTheWebhook() {
        slack.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value(notification.title()))
                .andExpect(jsonPath("$.blocks[0].text.text").value(
                        "*<http://localhost:3000/tests/ce1cbd14-a6b1-4100-8d48-2441ee64eb6f|Flaky test: "
                                + "CheckoutServiceTest.appliesDiscount in acme/checkout>*\n"
                                + notification.summary() + "\n>Passed and failed on the same commit 9f2c4e1"))
                .andExpect(jsonPath("$.blocks[1].elements[0].text").value("acme/checkout · SUSPICIOUS → FLAKY"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        channel.send(notification);

        slack.verify();
    }

    @Test
    void propagatesARejectedMessageSoTheDeliveryIsRetried() {
        slack.expect(requestTo(WEBHOOK)).andRespond(withServerError());

        assertThatThrownBy(() -> channel.send(notification)).isInstanceOf(RestClientException.class);
    }

    @Test
    void escapesSlackMarkupInText() {
        assertThat(SlackChannel.escape("a < b && c > d")).isEqualTo("a &lt; b &amp;&amp; c &gt; d");
    }

    @Test
    void isNamedSlack() {
        assertThat(channel.name()).isEqualTo("slack");
    }
}
