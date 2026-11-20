package dev.triaxis.assistant.domain.service;

import java.util.List;

import dev.triaxis.assistant.domain.exception.InvalidProtocolException;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolParserTest {

	private static final String HYPERTENSION = """
			# PROTO-001: Crisis Hipertensiva, manejo inicial

			## Definición

			Presión arterial ≥ 180/120 mmHg. Si hay daño de órgano diana es emergencia; si no, urgencia.

			## Medidas iniciales

			Reconfirmar la presión tras 5 minutos de reposo.
			No reducir la presión más de un 25% en la primera hora.
			""";

	private final ProtocolParser parser = new ProtocolParser();

	@Test
	void splitsAProtocolIntoOneChunkPerSection() {
		List<ProtocolChunk> chunks = parser.parse("PROTO-001.md", HYPERTENSION);

		assertThat(chunks).extracting(ProtocolChunk::id)
				.containsExactly("PROTO-001#definicion", "PROTO-001#medidas-iniciales");
		ProtocolChunk measures = chunks.get(1);
		assertThat(measures.protocolId()).isEqualTo("PROTO-001");
		assertThat(measures.protocolTitle()).isEqualTo("Crisis Hipertensiva, manejo inicial");
		assertThat(measures.section()).isEqualTo("Medidas iniciales");
		assertThat(measures.content()).isEqualTo(
				"Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25% en la primera hora.");
	}

	@Test
	void theTextToEmbedCarriesTheProtocolAndTheSection() {
		ProtocolChunk definition = parser.parse("PROTO-001.md", HYPERTENSION).get(0);

		assertThat(definition.embeddingText()).isEqualTo("Crisis Hipertensiva, manejo inicial. Definición. "
				+ "Presión arterial ≥ 180/120 mmHg. Si hay daño de órgano diana es emergencia; si no, urgencia.");
	}

	@Test
	void theHashChangesOnlyWhenTheContentChanges() {
		String same = parser.parse("PROTO-001.md", HYPERTENSION).get(0).contentHash();
		String again = parser.parse("PROTO-001.md", HYPERTENSION.replace("\n\n## Definición", "\n## Definición"))
				.get(0).contentHash();
		String changed = parser.parse("PROTO-001.md", HYPERTENSION.replace("180/120", "190/120")).get(0)
				.contentHash();

		assertThat(same).hasSize(64).isEqualTo(again).isNotEqualTo(changed);
	}

	@Test
	void sectionIdsIgnoreAccentsCaseAndPunctuation() {
		List<ProtocolChunk> chunks = parser.parse("PROTO-002.md", """
				# PROTO-002: Sepsis, identificación temprana (qSOFA)
				## Acciones  Iniciales (adultos)
				Lactato y hemocultivos antes del antibiótico.
				## Clasificación
				Leve.
				""");

		assertThat(chunks).extracting(ProtocolChunk::id)
				.containsExactly("PROTO-002#acciones-iniciales-adultos", "PROTO-002#clasificacion");
	}

	@Test
	void rejectsAFileWithoutTheTitleLine() {
		assertThatThrownBy(() -> parser.parse("broken.md", "## Definición\nTexto."))
				.isInstanceOf(InvalidProtocolException.class)
				.hasMessageContaining("broken.md");
	}

	@Test
	void rejectsAnIdThatIsNotAProtocolId() {
		assertThatThrownBy(() -> parser.parse("x.md", "# Hipertension: manejo\n## Definición\nTexto."))
				.isInstanceOf(InvalidProtocolException.class);
	}

	@Test
	void rejectsAProtocolWithoutSections() {
		assertThatThrownBy(() -> parser.parse("x.md", "# PROTO-009: Vacio\nTexto sin seccion."))
				.isInstanceOf(InvalidProtocolException.class);
	}

	@Test
	void rejectsAnEmptySection() {
		assertThatThrownBy(() -> parser.parse("x.md", "# PROTO-009: Vacio\n## Definición\n\n## Otra\nTexto."))
				.isInstanceOf(InvalidProtocolException.class)
				.hasMessageContaining("Definición");
	}

	@Test
	void rejectsTwoSectionsWithTheSameId() {
		assertThatThrownBy(() -> parser.parse("x.md", "# PROTO-009: Doble\n## Definición\nA.\n## Definicion\nB."))
				.isInstanceOf(InvalidProtocolException.class);
	}

}
