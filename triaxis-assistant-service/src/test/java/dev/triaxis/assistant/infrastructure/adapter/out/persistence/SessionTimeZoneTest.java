package dev.triaxis.assistant.infrastructure.adapter.out.persistence;

import java.util.TimeZone;

import dev.triaxis.assistant.AssistantIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/** The R2DBC session runs in UTC even though the test JVM does not (ASSUMPTIONS A12). */
@AssistantIntegrationTest
class SessionTimeZoneTest {

	@Autowired
	DatabaseClient database;

	@Test
	void theDatabaseSessionIsInUtcWhileTheJvmIsNot() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Bogota");
		assertThat(database.sql("SELECT current_setting('TimeZone')").map(row -> row.get(0, String.class)).one()
				.block()).isEqualTo("UTC");
	}

}
