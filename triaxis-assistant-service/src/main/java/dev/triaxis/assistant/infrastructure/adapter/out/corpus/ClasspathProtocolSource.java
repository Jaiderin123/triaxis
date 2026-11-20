package dev.triaxis.assistant.infrastructure.adapter.out.corpus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.triaxis.assistant.application.port.out.ProtocolSource;
import dev.triaxis.assistant.domain.model.ProtocolDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Reads every {@code *.md} file under {@code assistant.corpus.location}, sorted by name. File reads block. */
@Component
class ClasspathProtocolSource implements ProtocolSource {

	private final ResourcePatternResolver resolver;
	private final String pattern;

	ClasspathProtocolSource(@Value("${assistant.corpus.location:classpath:/protocols/}") String location) {
		this.resolver = new PathMatchingResourcePatternResolver();
		this.pattern = (location.endsWith("/") ? location : location + "/") + "*.md";
	}

	@Override
	public Mono<List<ProtocolDocument>> load() {
		return Mono.fromCallable(this::read).subscribeOn(Schedulers.boundedElastic());
	}

	/** A missing folder gives an empty corpus, which the ingestion rejects with a clear message. */
	private List<ProtocolDocument> read() throws IOException {
		Resource[] resources;
		try {
			resources = resolver.getResources(pattern);
		}
		catch (FileNotFoundException missing) {
			return List.of();
		}
		return Arrays.stream(resources)
				.sorted(Comparator.comparing(resource -> Objects.requireNonNullElse(resource.getFilename(), "")))
				.map(ClasspathProtocolSource::toDocument)
				.toList();
	}

	private static ProtocolDocument toDocument(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return new ProtocolDocument(resource.getFilename(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read protocol " + resource.getFilename(), e);
		}
	}

}
