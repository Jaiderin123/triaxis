package dev.triaxis.web;

import dev.triaxis.web.security.MetricsProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPropertiesTest {

	@Test
	void thePasswordOfTheScraperIsNeverPrinted() {
		assertThat(new MetricsProperties("metrics", "s3cr3t-value").toString())
				.doesNotContain("s3cr3t-value").contains("metrics").contains("password=***");
	}

}
