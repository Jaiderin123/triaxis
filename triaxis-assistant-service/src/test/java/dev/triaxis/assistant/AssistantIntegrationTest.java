package dev.triaxis.assistant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application context with a real PostgreSQL (pgvector) container and a test JWT decoder. Every integration
 * test uses this single configuration so Spring reuses one context and one set of containers.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(properties = {"spring.test.webtestclient.timeout=30s",
		"triaxis.web.metrics.username=scraper", "triaxis.web.metrics.password=test-metrics-secret",
		// The tests ask many questions with a few users; QuestionRateLimitApiTest checks the limit itself.
		"resilience4j.ratelimiter.configs.question.limit-for-period=100000"})
@AutoConfigureWebTestClient
@AutoConfigureObservability
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
public @interface AssistantIntegrationTest {
}
