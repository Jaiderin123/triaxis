package dev.triaxis.assistant.infrastructure.config;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.ResilientLlmAdapter;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.gemini.GeminiLlmAdapter;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.mock.MockLlmAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Chooses the provider with {@code assistant.llm.provider}: {@code mock} (default, no key) or {@code gemini}
 * (the {@code gemini} profile also turns on the Spring AI auto-configuration and reads {@code GEMINI_API_KEY}).
 * The application only sees the provider through {@link ResilientLlmAdapter}.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
class LlmConfiguration {

	static final String PROVIDER = "llmProvider";
	static final String BREAKER = "llm";

	@Bean(PROVIDER)
	@ConditionalOnProperty(prefix = "assistant.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
	LlmPort mockLlm() {
		return new MockLlmAdapter();
	}

	@Bean(PROVIDER)
	@ConditionalOnProperty(prefix = "assistant.llm", name = "provider", havingValue = "gemini")
	LlmPort geminiLlm(ChatModel chatModel, EmbeddingModel embeddingModel,
			@Value("${spring.ai.google.genai.embedding.text.options.model}") String embeddingModelName) {
		return new GeminiLlmAdapter(chatModel, embeddingModel, embeddingModelName);
	}

	@Bean
	@Primary
	LlmPort resilientLlm(@Qualifier(PROVIDER) LlmPort provider, CircuitBreakerRegistry breakers, MeterRegistry meters,
			LlmProperties properties) {
		return new ResilientLlmAdapter(provider, breakers.circuitBreaker(BREAKER), meters,
				properties.timeout().embedding(), properties.timeout().answer());
	}

}
