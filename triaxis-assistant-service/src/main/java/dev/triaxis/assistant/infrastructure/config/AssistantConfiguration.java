package dev.triaxis.assistant.infrastructure.config;

import java.time.Instant;

import dev.triaxis.assistant.application.port.in.AskClinicalQuestionUseCase;
import dev.triaxis.assistant.application.port.in.IngestCorpusUseCase;
import dev.triaxis.assistant.application.port.out.AssistantMetrics;
import dev.triaxis.assistant.application.port.out.ClockPort;
import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.application.port.out.ProtocolSource;
import dev.triaxis.assistant.application.service.AskClinicalQuestionService;
import dev.triaxis.assistant.application.service.CorpusState;
import dev.triaxis.assistant.application.service.IngestCorpusService;
import dev.triaxis.assistant.domain.service.CitationValidator;
import dev.triaxis.assistant.domain.service.ProtocolParser;
import dev.triaxis.assistant.domain.service.QuestionPolicy;
import dev.triaxis.assistant.domain.service.RetrievalPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain and application classes. */
@Configuration
@EnableConfigurationProperties({CorpusProperties.class, RagProperties.class})
class AssistantConfiguration {

	@Bean
	ClockPort clockPort() {
		return Instant::now;
	}

	@Bean
	ProtocolParser protocolParser() {
		return new ProtocolParser();
	}

	@Bean
	CorpusState corpusState() {
		return new CorpusState();
	}

	@Bean
	IngestCorpusUseCase ingestCorpusUseCase(ProtocolSource source, ProtocolChunkRepository chunks, LlmPort llm,
			ClockPort clock, ProtocolParser parser, CorpusState state) {
		return new IngestCorpusService(source, chunks, llm, clock, parser, state);
	}

	@Bean
	AskClinicalQuestionUseCase askClinicalQuestionUseCase(LlmPort llm, ProtocolChunkRepository chunks,
			CorpusState corpus, AssistantMetrics metrics, RagProperties rag) {
		return new AskClinicalQuestionService(llm, chunks, corpus, metrics, new QuestionPolicy(rag.questionMaxLength()),
				new RetrievalPolicy(rag.topK(), rag.maxDistance()), new CitationValidator());
	}

}
