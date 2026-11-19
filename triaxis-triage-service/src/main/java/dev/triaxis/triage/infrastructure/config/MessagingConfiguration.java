package dev.triaxis.triage.infrastructure.config;

import dev.triaxis.common.event.TriageEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
class MessagingConfiguration {

	/** Created at startup when missing (the compose file and the manifests create it too). */
	@Bean
	NewTopic triageEventsTopic(OutboxProperties properties) {
		return TopicBuilder.name(TriageEvent.TOPIC)
				.partitions(properties.topic().partitions())
				.replicas(properties.topic().replicas())
				.build();
	}

}
