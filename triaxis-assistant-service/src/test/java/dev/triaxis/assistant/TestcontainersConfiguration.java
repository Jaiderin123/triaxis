package dev.triaxis.assistant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The same images as the compose file. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** PostgreSQL 16 with the pgvector extension. */
	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(
				DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
	}

}
