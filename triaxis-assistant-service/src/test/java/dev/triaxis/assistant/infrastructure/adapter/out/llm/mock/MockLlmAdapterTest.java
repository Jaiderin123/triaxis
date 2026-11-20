package dev.triaxis.assistant.infrastructure.adapter.out.llm.mock;

import java.util.List;

import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmAdapterTest {

	private final MockLlmAdapter llm = new MockLlmAdapter();

	private static double cosine(float[] a, float[] b) {
		double dot = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
		}
		return dot;
	}

	@Test
	void theSameTextGivesTheSameUnitVector() {
		float[] first = llm.embed("Medidas iniciales ante una crisis hipertensiva").block();
		float[] second = llm.embed("Medidas iniciales ante una crisis hipertensiva").block();

		assertThat(first).hasSize(BagOfWordsEmbedding.DIMENSIONS).containsExactly(second);
		assertThat(cosine(first, first)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
	}

	@Test
	void accentsCaseAndCommonWordsDoNotMatter() {
		assertThat(BagOfWordsEmbedding.stems("¿Cuándo se administra el primer ANTIBIÓTICO?"))
				.containsExactly("admin", "prime", "antib");
	}

	@Test
	void textsThatShareWordsAreCloserThanTextsThatDoNot() {
		float[] question = llm.embed("primer antibiótico en sepsis").block();
		float[] sepsis = llm.embed("Sepsis. Acciones iniciales. Antibiótico de amplio espectro").block();
		float[] dehydration = llm.embed("Deshidratación pediátrica. Rehidratación. Solución oral").block();

		assertThat(cosine(question, sepsis)).isGreaterThan(cosine(question, dehydration));
	}

	@Test
	void aTextWithoutContentWordsStillHasAVector() {
		assertThat(llm.embed("¿y el de la?").block()).hasSize(BagOfWordsEmbedding.DIMENSIONS);
	}

	@Test
	void theAnswerIsTheClosestExcerptCited() {
		ProtocolChunk chunk = ProtocolChunk.of("PROTO-001#medidas-iniciales", "PROTO-001", "Crisis",
				"Medidas iniciales", "Reconfirmar la presión.");
		ProtocolChunk other = ProtocolChunk.of("PROTO-001#definicion", "PROTO-001", "Crisis", "Definición", "Otro.");

		StepVerifier.create(llm.answer("pregunta", List.of(new NumberedExcerpt(1, chunk), new NumberedExcerpt(2, other))))
				.expectNext(new ModelAnswer("Reconfirmar la presión.", List.of(1), false))
				.verifyComplete();
	}

	@Test
	void withoutExcerptsThereIsNoProtocol() {
		StepVerifier.create(llm.answer("pregunta", List.of())).expectNext(ModelAnswer.noProtocolFound())
				.verifyComplete();
	}

}
