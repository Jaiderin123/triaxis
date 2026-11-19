package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the outbox relay and of the topic it publishes to.
 *
 * @param relay  delivery loop settings
 * @param topic  settings used to create the topic when it does not exist
 */
@ConfigurationProperties("triage.outbox")
public record OutboxProperties(@DefaultValue Relay relay, @DefaultValue Topic topic) {

	public record Relay(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("PT1S") Duration interval,
			@DefaultValue("50") int batchSize,
			@DefaultValue("PT15S") Duration sendTimeout) {
	}

	public record Topic(@DefaultValue("3") int partitions, @DefaultValue("1") short replicas) {
	}

}
