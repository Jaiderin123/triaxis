package dev.triaxis.assistant.infrastructure.adapter.out.corpus;

import dev.triaxis.assistant.domain.model.ProtocolDocument;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathProtocolSourceTest {

	@Test
	void readsTheProtocolFilesInNameOrder() {
		StepVerifier.create(new ClasspathProtocolSource("classpath:/protocols/").load())
				.assertNext(documents -> {
					assertThat(documents).extracting(ProtocolDocument::source)
							.containsExactly("PROTO-001.md", "PROTO-002.md", "PROTO-003.md");
					assertThat(documents.get(0).markdown()).startsWith("# PROTO-001: Crisis Hipertensiva");
				})
				.verifyComplete();
	}

	@Test
	void anEmptyLocationGivesNoDocuments() {
		StepVerifier.create(new ClasspathProtocolSource("classpath:/no-such-folder/").load())
				.assertNext(documents -> assertThat(documents).isEmpty())
				.verifyComplete();
	}

}
