package dev.triaxis.triage.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingConfigurationTest {

	@Test
	void theTopicKeepsTheEventsSevenDays() {
		OutboxProperties properties = new OutboxProperties(null, new OutboxProperties.Topic(3, (short) 1));

		NewTopic topic = new MessagingConfiguration().triageEventsTopic(properties);

		assertThat(topic.name()).isEqualTo("triaje.eventos");
		assertThat(topic.numPartitions()).isEqualTo(3);
		assertThat(topic.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG, "604800000");
	}

}
