package dev.triaxis.assistant.application.port.out;

import java.util.List;

import dev.triaxis.assistant.domain.model.ProtocolDocument;
import reactor.core.publisher.Mono;

/** Where the protocol files of the corpus come from. */
public interface ProtocolSource {

	Mono<List<ProtocolDocument>> load();

}
