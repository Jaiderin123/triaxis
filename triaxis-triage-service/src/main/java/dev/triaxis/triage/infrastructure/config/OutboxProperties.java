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
			@DefaultValue("PT15S") Duration sendTimeout,
			@DefaultValue("PT30S") Duration cycleBudget,
			@DefaultValue("PT60S") Duration cycleTimeout) {

		/**
		 * {@code cycleBudget}: after it, the cycle stops between two events and commits what it sent (at least one
		 * event per cycle, so the relay always progresses). {@code cycleTimeout}: a safety net for a stuck cycle,
		 * which is rolled back; it must be longer than the budget plus one send, or a slow but working Kafka
		 * would be cut in the middle of every cycle and the same events sent forever.
		 */
		public Relay {
			if (batchSize < 1) {
				throw new IllegalArgumentException("triage.outbox.relay.batch-size must be at least 1");
			}
			requirePositive(interval, "interval");
			requirePositive(sendTimeout, "send-timeout");
			requirePositive(cycleBudget, "cycle-budget");
			requirePositive(cycleTimeout, "cycle-timeout");
			if (cycleTimeout.compareTo(cycleBudget.plus(sendTimeout)) <= 0) {
				throw new IllegalArgumentException(
						"triage.outbox.relay.cycle-timeout must be longer than cycle-budget plus send-timeout");
			}
		}

		private static void requirePositive(Duration value, String name) {
			if (value == null || value.isZero() || value.isNegative()) {
				throw new IllegalArgumentException("triage.outbox.relay." + name + " must be positive");
			}
		}

	}

	public record Topic(@DefaultValue("3") int partitions, @DefaultValue("1") short replicas) {
	}

}
