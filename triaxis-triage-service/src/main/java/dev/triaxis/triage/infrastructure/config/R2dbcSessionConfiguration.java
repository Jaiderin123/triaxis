package dev.triaxis.triage.infrastructure.config;

import java.util.TimeZone;

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Settings of every database session. A customizer is used instead of {@code spring.r2dbc.properties} because it
 * also applies when the connection comes from connection details (Testcontainers, service bindings).
 */
@Configuration
class R2dbcSessionConfiguration {

	/** Sessions run in UTC whatever the JVM zone is, so {@code date_trunc} gives UTC hours (ASSUMPTIONS A12). */
	@Bean
	ConnectionFactoryOptionsBuilderCustomizer utcSession() {
		return options -> options.option(PostgresqlConnectionFactoryProvider.TIME_ZONE, TimeZone.getTimeZone("UTC"));
	}

}
