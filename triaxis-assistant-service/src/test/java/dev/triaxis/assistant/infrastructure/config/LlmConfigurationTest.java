package dev.triaxis.assistant.infrastructure.config;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.gemini.GeminiLlmAdapter;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.mock.MockLlmAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.ResilientLlmAdapter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LlmConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(LlmConfiguration.class)
			.withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new);

	@Test
	void theMockIsTheDefaultProvider() {
		runner.run(context -> {
			assertThat(context.getBean(LlmConfiguration.PROVIDER, LlmPort.class)).isInstanceOf(MockLlmAdapter.class);
			assertThat(context.getBean(LlmPort.class)).isInstanceOf(ResilientLlmAdapter.class);
		});
	}

	@Test
	void theGeminiProviderUsesTheSpringAiModels() {
		runner.withPropertyValues("assistant.llm.provider=gemini",
						"spring.ai.google.genai.embedding.text.options.model=gemini-embedding-001")
				.withBean(ChatModel.class, () -> mock(ChatModel.class))
				.withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
				.run(context -> {
					assertThat(context.getBean(LlmConfiguration.PROVIDER, LlmPort.class))
							.isInstanceOf(GeminiLlmAdapter.class);
					assertThat(context.getBean(LlmPort.class).embeddingModel()).isEqualTo("gemini-embedding-001");
				});
	}

}
