package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.util.TimeZone;

import dev.triaxis.triage.TriageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Settings of every R2DBC session: UTC even though the test JVM is not (A12), and time limits. */
@TriageIntegrationTest
class SessionTimeZoneTest {

	@Autowired
	DatabaseClient database;

	private String query(String sql) {
		return database.sql(sql).map(row -> row.get(0, String.class)).one().block();
	}

	@Test
	void theJvmOfTheTestsIsNotInUtc() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Bogota");
	}

	@Test
	void theDatabaseSessionIsInUtc() {
		assertThat(query("SELECT current_setting('TimeZone')")).isEqualTo("UTC");
	}

	@Test
	void statementsAndLockWaitsHaveATimeLimit() {
		assertThat(query("SELECT current_setting('statement_timeout')")).isEqualTo("10s");
		assertThat(query("SELECT current_setting('lock_timeout')")).isEqualTo("5s");
	}

	@Test
	void hourlyBucketsAreUtcHours() {
		assertThat(query("SELECT date_trunc('hour', TIMESTAMPTZ '2026-11-19T23:30:00Z')::text"))
				.isEqualTo("2026-11-19 23:00:00+00");
	}

}
