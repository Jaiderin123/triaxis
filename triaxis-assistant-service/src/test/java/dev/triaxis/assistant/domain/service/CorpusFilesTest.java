package dev.triaxis.assistant.domain.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.triaxis.assistant.domain.model.ProtocolChunk;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The protocol files shipped with the service parse into the sections of the official corpus. */
class CorpusFilesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			PROTO-001 | Crisis Hipertensiva, manejo inicial         | Definición, Medidas iniciales, Contraindicaciones
			PROTO-002 | Sepsis, identificación temprana (qSOFA)     | Criterios, Acciones iniciales
			PROTO-003 | Deshidratación pediátrica                   | Clasificación, Rehidratación
			""")
	void eachProtocolHasItsSections(String id, String title, String sections) throws IOException {
		List<ProtocolChunk> chunks = new ProtocolParser().parse(id + ".md", read("/protocols/" + id + ".md"));

		assertThat(chunks).allSatisfy(chunk -> {
			assertThat(chunk.protocolId()).isEqualTo(id);
			assertThat(chunk.protocolTitle()).isEqualTo(title);
			assertThat(chunk.content()).isNotBlank();
		});
		assertThat(chunks).extracting(ProtocolChunk::section).containsExactly(sections.split(",\\s*"));
	}

	private static String read(String path) throws IOException {
		try (InputStream in = CorpusFilesTest.class.getResourceAsStream(path)) {
			assertThat(in).as(path).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
