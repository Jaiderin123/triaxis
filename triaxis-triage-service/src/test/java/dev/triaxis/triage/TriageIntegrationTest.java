package dev.triaxis.triage;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application context with real PostgreSQL and Kafka containers and a test JWT decoder. The outbox relay loop is off, so tests call it explicitly and stay deterministic. All
 * integration tests use this single configuration so Spring reuses one context (and one set of containers).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(properties = {"spring.test.webtestclient.timeout=30s", "triage.outbox.relay.enabled=false"})
@AutoConfigureWebTestClient
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
public @interface TriageIntegrationTest {
}
