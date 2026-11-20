package dev.triaxis.assistant.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.assistant.application.port.in.ApplyTriageEventUseCase;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import org.springframework.kafka.listener.MessageListenerContainer;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The check of the view when partitions are assigned, with Redis answering or not. */
class UrgencyViewListenerStartupTest {

	private final UrgencyViewStore store = mock(UrgencyViewStore.class);
	private final MessageListenerContainer container = mock(MessageListenerContainer.class);
	private final ConsumerSeekCallback callback = mock(ConsumerSeekCallback.class);
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final Map<TopicPartition, Long> assigned = Map.of(new TopicPartition("triaje.eventos", 0), 0L);

	@SuppressWarnings("unchecked")
	private UrgencyViewListener listener(Duration backoff, Duration maxBackoff) {
		KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
		when(registry.getListenerContainer(UrgencyViewListener.CONTAINER)).thenReturn(container);
		ObjectProvider<KafkaListenerEndpointRegistry> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(registry);
		return new UrgencyViewListener(mock(ApplyTriageEventUseCase.class), store,
				Mappers.getMapper(TriageEventMapper.class), new ObjectMapper(), meters, provider, 64, backoff,
				maxBackoff);
	}

	private UrgencyViewListener listener() {
		return listener(Duration.ofMillis(1), Duration.ofMillis(4));
	}

	@Test
	void anUnbuiltViewIsResetAndTheTopicReplayed() {
		when(store.isInitialized()).thenReturn(Mono.just(false));
		when(store.reset()).thenReturn(Mono.empty());

		listener().onPartitionsAssigned(assigned, callback);

		verify(store).reset();
		verify(callback).seekToBeginning(assigned.keySet());
	}

	@Test
	void aBuiltViewIsLeftAlone() {
		when(store.isInitialized()).thenReturn(Mono.just(true));

		listener().onPartitionsAssigned(assigned, callback);

		verify(store, never()).reset();
		verify(callback, never()).seekToBeginning(anyCollection());
	}

	/** The consumer does not leave the assignment (so it consumes nothing) until Redis answers the check. */
	@Test
	void whenRedisDoesNotAnswerTheConsumerWaitsUntilItDoes() {
		when(container.isRunning()).thenReturn(true);
		when(store.isInitialized()).thenReturn(Mono.error(new IllegalStateException("Redis down")),
				Mono.error(new IllegalStateException("Redis down")), Mono.just(false));
		when(store.reset()).thenReturn(Mono.empty());

		listener().onPartitionsAssigned(assigned, callback);

		verify(store, times(3)).isInitialized();
		verify(callback).seekToBeginning(assigned.keySet());
		assertThat(meters.counter("assistant.urgency.startup.failures").count()).isEqualTo(2);
	}

	@Test
	void theWaitEndsWhenTheListenerStops() {
		when(container.isRunning()).thenReturn(true, true, false);
		when(store.isInitialized()).thenReturn(Mono.error(new IllegalStateException("Redis down")));

		listener().onPartitionsAssigned(assigned, callback);

		verify(store, times(3)).isInitialized();
		verify(callback, never()).seekToBeginning(anyCollection());
	}

	@Test
	void theSettingsAreValidated() {
		assertThatThrownBy(() -> listener(Duration.ZERO, Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> listener(Duration.ofSeconds(2), Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
