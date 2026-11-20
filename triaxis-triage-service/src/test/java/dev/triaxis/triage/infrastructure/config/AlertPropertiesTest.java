package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;

import dev.triaxis.triage.infrastructure.config.AlertProperties.Channel;
import dev.triaxis.triage.infrastructure.config.AlertProperties.Webhook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertPropertiesTest {

	private static final Duration TWO = Duration.ofSeconds(2);
	private static final Duration FIVE = Duration.ofSeconds(5);

	private static Webhook webhook(String url) {
		return new Webhook(url, TWO, FIVE);
	}

	@Test
	void theLogChannelNeedsNoUrl() {
		AlertProperties properties = new AlertProperties(Channel.LOG, 3, Duration.ofSeconds(1), webhook(""));

		assertThat(properties.webhook().uri()).isNull();
	}

	@Test
	void theWebhookChannelNeedsAnAbsoluteHttpUrl() {
		for (String invalid : new String[] {"", "  ", "not a url", "ftp://example.org/hook", "/relative/hook"}) {
			assertThatThrownBy(() -> new AlertProperties(Channel.WEBHOOK, 3, Duration.ofSeconds(1), webhook(invalid)))
					.as(invalid)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageNotContaining(invalid.isBlank() ? "\u0000" : invalid);
		}
		AlertProperties valid = new AlertProperties(Channel.WEBHOOK, 3, Duration.ofSeconds(1),
				webhook("https://hooks.example.org/services/T0/B0/x"));
		assertThat(valid.webhook().uri()).hasHost("hooks.example.org");
	}

	@Test
	void attemptsBackoffAndTimeoutsAreValidated() {
		assertThatThrownBy(() -> new AlertProperties(Channel.LOG, 0, Duration.ofSeconds(1), webhook("")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AlertProperties(Channel.LOG, 3, Duration.ZERO, webhook("")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Webhook("", Duration.ZERO, FIVE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Webhook("", TWO, Duration.ofSeconds(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theUrlIsNeverPrinted() {
		AlertProperties properties = new AlertProperties(Channel.WEBHOOK, 3, Duration.ofSeconds(1),
				webhook("https://hooks.example.org/services/secret-token"));

		assertThat(properties.toString()).doesNotContain("secret-token", "hooks.example.org").contains("<secret>");
	}

}
