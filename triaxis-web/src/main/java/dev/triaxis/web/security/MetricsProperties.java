package dev.triaxis.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials of the metrics scraper. They only come from the environment ({@code METRICS_USER} and
 * {@code METRICS_PASSWORD}); without a password the metrics endpoint is denied to everybody.
 */
@ConfigurationProperties("triaxis.web.metrics")
public record MetricsProperties(@DefaultValue("metrics") String username, String password) {

	@Override
	public String toString() {
		return "MetricsProperties[username=" + username + ", password=***]";
	}

}
