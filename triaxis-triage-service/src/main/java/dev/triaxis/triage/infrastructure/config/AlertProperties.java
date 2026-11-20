package dev.triaxis.triage.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the alerts to the medical team (RF-06, ADR 0011).
 *
 * @param channel  {@code log} (default, no external account) or {@code webhook}
 * @param attempts tries of one alert, in total, before it is given up as failed
 * @param backoff  wait before the second try; it doubles before each next one
 * @param webhook  the webhook channel; its URL comes from {@code ALERT_WEBHOOK_URL} only
 */
@ConfigurationProperties("triage.alerts")
public record AlertProperties(
		@DefaultValue("log") Channel channel,
		@DefaultValue("3") int attempts,
		@DefaultValue("PT1S") Duration backoff,
		@DefaultValue Webhook webhook) {

	public enum Channel {
		LOG,
		WEBHOOK
	}

	public AlertProperties {
		if (attempts < 1) {
			throw new IllegalArgumentException("triage.alerts.attempts must be at least 1");
		}
		if (!positive(backoff)) {
			throw new IllegalArgumentException("triage.alerts.backoff must be positive");
		}
		if (channel == Channel.WEBHOOK && webhook.uri() == null) {
			throw new IllegalArgumentException(
					"triage.alerts.channel is webhook: ALERT_WEBHOOK_URL must be an absolute http or https URL");
		}
	}

	/**
	 * @param url             secret: it is never logged (see {@link #toString()})
	 * @param connectTimeout  longest wait for the connection
	 * @param responseTimeout longest wait for the answer once the request was sent
	 */
	public record Webhook(
			@DefaultValue("") String url,
			@DefaultValue("PT2S") Duration connectTimeout,
			@DefaultValue("PT5S") Duration responseTimeout) {

		public Webhook {
			if (!positive(connectTimeout) || !positive(responseTimeout)) {
				throw new IllegalArgumentException(
						"triage.alerts.webhook.connect-timeout and response-timeout must be positive");
			}
		}

		/** The URL when it is an absolute http or https URL, otherwise null. */
		public URI uri() {
			if (url == null || url.isBlank()) {
				return null;
			}
			try {
				URI uri = URI.create(url.strip());
				boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
				return http && uri.getHost() != null ? uri : null;
			}
			catch (IllegalArgumentException invalid) {
				return null;
			}
		}

		@Override
		public String toString() {
			return "Webhook[url=" + (url == null || url.isBlank() ? "<unset>" : "<secret>") + ", connectTimeout="
					+ connectTimeout + ", responseTimeout=" + responseTimeout + "]";
		}

	}

	private static boolean positive(Duration duration) {
		return duration != null && !duration.isZero() && !duration.isNegative();
	}

}
