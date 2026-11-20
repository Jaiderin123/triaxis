package dev.triaxis.assistant.domain.service;

import java.util.List;

import dev.triaxis.assistant.domain.exception.InvalidQuestionException;
import dev.triaxis.assistant.domain.model.AnswerOutcome;
import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.Source;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerRulesTest {

	private static final ProtocolChunk MEASURES = ProtocolChunk.of("PROTO-001#medidas-iniciales", "PROTO-001",
			"Crisis Hipertensiva", "Medidas iniciales", "Reconfirmar la presión.");
	private static final ProtocolChunk DEFINITION = ProtocolChunk.of("PROTO-001#definicion", "PROTO-001",
			"Crisis Hipertensiva", "Definición", "Presión ≥ 180/120.");
	private static final ProtocolChunk SEPSIS = ProtocolChunk.of("PROTO-002#acciones-iniciales", "PROTO-002",
			"Sepsis", "Acciones iniciales", "Antibiótico en la primera hora.");

	@Nested
	class Question {

		private final QuestionPolicy policy = new QuestionPolicy(20);

		@Test
		void aQuestionIsTrimmed() {
			assertThat(policy.validate("  ¿Sepsis?  ")).isEqualTo("¿Sepsis?");
		}

		@Test
		void aMissingOrBlankQuestionIsRejected() {
			assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(InvalidQuestionException.class);
			assertThatThrownBy(() -> policy.validate(" \n\t")).isInstanceOf(InvalidQuestionException.class);
		}

		@Test
		void theLimitIsInclusive() {
			assertThat(policy.validate("x".repeat(20))).hasSize(20);
			assertThatThrownBy(() -> policy.validate("x".repeat(21)))
					.isInstanceOf(InvalidQuestionException.class)
					.hasMessageContaining("20");
		}

	}

	@Nested
	class Retrieval {

		private final RetrievalPolicy policy = new RetrievalPolicy(2, 0.70);

		@Test
		void keepsOnlyExcerptsWithinTheThresholdClosestFirstAndNumbersThem() {
			List<NumberedExcerpt> relevant = policy.relevant(List.of(new RetrievedExcerpt(DEFINITION, 0.61),
					new RetrievedExcerpt(SEPSIS, 0.90), new RetrievedExcerpt(MEASURES, 0.47)));

			assertThat(relevant).containsExactly(new NumberedExcerpt(1, MEASURES), new NumberedExcerpt(2, DEFINITION));
		}

		@Test
		void theThresholdIsInclusiveAndTopKLimits() {
			assertThat(policy.relevant(List.of(new RetrievedExcerpt(MEASURES, 0.70)))).hasSize(1);
			assertThat(policy.relevant(List.of(new RetrievedExcerpt(MEASURES, 0.1), new RetrievedExcerpt(DEFINITION, 0.2),
					new RetrievedExcerpt(SEPSIS, 0.3)))).hasSize(2);
		}

		@Test
		void nothingCloseEnoughGivesNoExcerpt() {
			assertThat(policy.relevant(List.of(new RetrievedExcerpt(SEPSIS, 0.82)))).isEmpty();
			assertThat(policy.relevant(List.of())).isEmpty();
		}

		@Test
		void rejectsInvalidSettings() {
			assertThatThrownBy(() -> new RetrievalPolicy(0, 0.5)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new RetrievalPolicy(3, 0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new RetrievalPolicy(3, 2)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Citations {

		private final CitationValidator validator = new CitationValidator();
		private final List<NumberedExcerpt> sent = List.of(new NumberedExcerpt(1, MEASURES),
				new NumberedExcerpt(2, DEFINITION), new NumberedExcerpt(3, SEPSIS));

		@Test
		void sourcesComeFromTheCitedExcerptsInTheOrderTheyWereSent() {
			ClinicalAnswer answer = validator.validate(new ModelAnswer("Reconfirmar.", List.of(3, 1, 1), false), sent);

			assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
			assertThat(answer.text()).isEqualTo("Reconfirmar.");
			assertThat(answer.sources()).containsExactly(new Source("PROTO-001", "Medidas iniciales"),
					new Source("PROTO-002", "Acciones iniciales"));
		}

		@Test
		void twoExcerptsOfTheSameSectionGiveOneSource() {
			List<NumberedExcerpt> twice = List.of(new NumberedExcerpt(1, MEASURES), new NumberedExcerpt(2, MEASURES));

			assertThat(validator.validate(new ModelAnswer("Texto.", List.of(1, 2), false), twice).sources())
					.containsExactly(new Source("PROTO-001", "Medidas iniciales"));
		}

		@Test
		void aNumberThatWasNotSentRejectsTheWholeAnswer() {
			assertThat(validator.validate(new ModelAnswer("Texto.", List.of(1, 4), false), sent))
					.isEqualTo(ClinicalAnswer.rejected());
		}

		@Test
		void anAnswerWithoutCitationsIsRejected() {
			assertThat(validator.validate(new ModelAnswer("Texto inventado.", List.of(), false), sent))
					.isEqualTo(ClinicalAnswer.rejected());
		}

		@Test
		void anEmptyOrUnusableAnswerIsRejected() {
			assertThat(validator.validate(new ModelAnswer(" ", List.of(1), false), sent)).isEqualTo(ClinicalAnswer.rejected());
			assertThat(validator.validate(ModelAnswer.unusable(), sent)).isEqualTo(ClinicalAnswer.rejected());
		}

		@Test
		void theModelSayingNoProtocolGivesNoProtocolEvenWithText() {
			assertThat(validator.validate(new ModelAnswer("algo", List.of(1), true), sent))
					.isEqualTo(ClinicalAnswer.noProtocol());
		}

		@Test
		void onlyAnAnsweredQuestionCarriesTextOrSources() {
			assertThatThrownBy(() -> new ClinicalAnswer(AnswerOutcome.NO_PROTOCOL, "texto", List.of()))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ClinicalAnswer.answered("texto", List.of()))
					.isInstanceOf(IllegalArgumentException.class);
		}

	}

}
