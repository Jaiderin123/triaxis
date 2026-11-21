package dev.triaxis.assistant.domain.service;

import java.util.List;

import dev.triaxis.assistant.domain.model.AnswerOutcome;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A figure that is not written in a cited excerpt rejects the answer (ADR 0025). */
class NumericGroundingTest {

	private static final ProtocolChunk SEPSIS = ProtocolChunk.of("PROTO-002#acciones-iniciales", "PROTO-002",
			"Sepsis, identificación temprana (qSOFA)", "Acciones iniciales",
			"Lactato y hemocultivos antes del antibiótico. Antibiótico de amplio espectro en la primera hora. "
					+ "Fluidoterapia con 30 ml/kg de cristaloides si hay hipotensión o lactato ≥ 4.");
	private static final ProtocolChunk HYPERTENSION = ProtocolChunk.of("PROTO-001#medidas-iniciales", "PROTO-001",
			"Crisis Hipertensiva, manejo inicial", "Medidas iniciales",
			"Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25% en la primera hora.");
	private static final List<NumberedExcerpt> SENT = List.of(new NumberedExcerpt(1, SEPSIS),
			new NumberedExcerpt(2, HYPERTENSION));

	private final CitationValidator validator = new CitationValidator();

	private AnswerOutcome check(String text, Integer... cited) {
		return validator.validate(new ModelAnswer(text, List.of(cited), false), SENT).outcome();
	}

	@Test
	void figuresWrittenInTheCitedExcerptsAreAccepted() {
		assertThat(check("Fluidoterapia con 30 ml/kg de cristaloides si el lactato es ≥ 4.", 1))
				.isEqualTo(AnswerOutcome.ANSWERED);
		assertThat(check("Reconfirmar tras 5 minutos y no reducir más de un 25 % en la primera hora.", 2))
				.isEqualTo(AnswerOutcome.ANSWERED);
		assertThat(check("Según PROTO-002 [1], el antibiótico va en la primera hora.", 1))
				.isEqualTo(AnswerOutcome.ANSWERED);
	}

	@Test
	void anInventedDoseIsRejected() {
		assertThat(check("Administrar 500 mg de amoxicilina en la primera hora.", 1))
				.isEqualTo(AnswerOutcome.REJECTED);
		assertThat(check("Fluidoterapia con 50 ml/kg de cristaloides.", 1)).isEqualTo(AnswerOutcome.REJECTED);
	}

	@Test
	void aFigureOfAnExcerptThatIsNotCitedIsRejected() {
		assertThat(check("Reconfirmar la presión tras 5 minutos de reposo.", 1)).isEqualTo(AnswerOutcome.REJECTED);
	}

	@Test
	void decimalsWithACommaOrAPointAndLeadingZerosAreTheSameNumber() {
		assertThat(CitationValidator.numbers("38,5 °C, 38.5 y PROTO-002")).containsExactly("38.5", "2");
	}

}
