package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;
import java.util.TimeZone;

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import org.springframework.beans.factory.annotation.Value;
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

	/**
	 * PostgreSQL ends a statement or a lock wait that takes longer than these limits, so a slow query or a held
	 * lock never keeps a connection, a request or a background cycle waiting forever.
	 */
	@Bean
	ConnectionFactoryOptionsBuilderCustomizer sessionTimeouts(
			@Value("${triaxis.database.statement-timeout:PT10S}") Duration statementTimeout,
			@Value("${triaxis.database.lock-timeout:PT5S}") Duration lockTimeout) {
		if (statementTimeout.isZero() || statementTimeout.isNegative() || lockTimeout.isZero()
				|| lockTimeout.isNegative()) {
			throw new IllegalArgumentException("triaxis.database.statement-timeout and lock-timeout must be positive");
		}
		return options -> options.option(PostgresqlConnectionFactoryProvider.STATEMENT_TIMEOUT, statementTimeout)
				.option(PostgresqlConnectionFactoryProvider.LOCK_WAIT_TIMEOUT, lockTimeout);
	}

}
