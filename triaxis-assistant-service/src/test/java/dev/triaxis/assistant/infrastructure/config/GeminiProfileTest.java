package dev.triaxis.assistant.infrastructure.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The gemini profile turns off the retries of Spring AI, which the Gemini models use (ADR 0004). */
class GeminiProfileTest {

	@Test
	void theGeminiProfileMakesASingleAttempt() throws IOException {
		List<PropertySource<?>> profile = new YamlPropertySourceLoader().load("gemini",
				new ClassPathResource("application-gemini.yaml"));
		Object maxAttempts = profile.get(0).getProperty("spring.ai.retry.max-attempts");
		assertThat(maxAttempts).hasToString("1");

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
				.withPropertyValues("spring.ai.retry.max-attempts=" + maxAttempts)
				.run(context -> {
					AtomicInteger attempts = new AtomicInteger();
					RetryTemplate retry = context.getBean(RetryTemplate.class);
					assertThatThrownBy(() -> retry.execute(attempt -> {
						attempts.incrementAndGet();
						throw new org.springframework.ai.retry.TransientAiException("HTTP 503");
					})).isInstanceOf(org.springframework.ai.retry.TransientAiException.class);
					assertThat(attempts).hasValue(1);
				});
	}

}
