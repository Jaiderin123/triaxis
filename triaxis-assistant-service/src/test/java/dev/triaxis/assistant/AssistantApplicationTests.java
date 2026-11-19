package dev.triaxis.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.r2dbc.url=r2dbc:postgresql://localhost:5432/assistant",
		"spring.r2dbc.username=test",
		"spring.r2dbc.password=test"
})
class AssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
