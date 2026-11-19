package dev.triaxis.triage;

import org.springframework.boot.SpringApplication;

public class TestTriaxisTriageServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(TriaxisTriageServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
