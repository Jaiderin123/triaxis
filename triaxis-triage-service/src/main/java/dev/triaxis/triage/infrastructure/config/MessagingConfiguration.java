package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;

import dev.triaxis.common.event.TriageEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
class MessagingConfiguration {

	/** Events are kept 7 days (ADR 0021), whoever creates the topic first: this bean, the compose file or the Job. */
	static final String RETENTION_MS = String.valueOf(Duration.ofDays(7).toMillis());

	/** Created at startup when missing (the compose file and the manifests create it too, with the same settings). */
	@Bean
	NewTopic triageEventsTopic(OutboxProperties properties) {
		return TopicBuilder.name(TriageEvent.TOPIC)
				.partitions(properties.topic().partitions())
				.replicas(properties.topic().replicas())
				.config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_MS)
				.build();
	}

}
