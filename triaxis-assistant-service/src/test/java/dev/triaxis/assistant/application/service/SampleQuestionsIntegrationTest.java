package dev.triaxis.assistant.application.service;

import java.util.List;

import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.application.port.in.AskClinicalQuestionUseCase;
import dev.triaxis.assistant.domain.model.AnswerOutcome;
import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three sample questions of the specification with the mock provider, the real corpus and pgvector. This is
 * also the calibration check of {@code assistant.rag.max-distance} for the mock embedding.
 */
@AssistantIntegrationTest
class SampleQuestionsIntegrationTest {

	@Autowired
	AskClinicalQuestionUseCase assistant;

	@Autowired
	CorpusState corpus;

	@BeforeEach
	void waitForTheCorpus() throws InterruptedException {
		for (int i = 0; i < 400 && !corpus.isReady(); i++) {
			Thread.sleep(50);
		}
		assertThat(corpus.isReady()).isTrue();
	}

	@Test
	void hypertensionIsAnsweredFromProto001() {
		ClinicalAnswer answer = assistant.ask("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?").block();

		assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
		assertThat(answer.text()).contains("Reconfirmar la presión tras 5 minutos de reposo");
		assertThat(answer.sources()).containsExactly(new Source("PROTO-001", "Medidas iniciales"));
	}

	@Test
	void sepsisIsAnsweredFromProto002() {
		ClinicalAnswer answer = assistant.ask("¿Cuándo se administra el primer antibiótico en sepsis?").block();

		assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
		assertThat(answer.text()).contains("Antibiótico de amplio espectro en la primera hora");
		assertThat(answer.sources()).containsExactly(new Source("PROTO-002", "Acciones iniciales"));
	}

	@Test
	void diabetesHasNoProtocol() {
		ClinicalAnswer answer = assistant.ask("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?")
				.block();

		assertThat(answer).isEqualTo(ClinicalAnswer.noProtocol());
		assertThat(answer.sources()).isEqualTo(List.of());
	}

}
