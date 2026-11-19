package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.AuditEntry;
import reactor.core.publisher.Mono;

/** Append-only: there is deliberately no update or delete operation. */
public interface AuditRepository {

	Mono<AuditEntry> append(AuditEntry entry);

}
