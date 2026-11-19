package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.mapper.AuditEntityMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
class R2dbcAuditRepository implements AuditRepository {

	private final R2dbcEntityTemplate template;
	private final AuditEntityMapper mapper;

	R2dbcAuditRepository(R2dbcEntityTemplate template, AuditEntityMapper mapper) {
		this.template = template;
		this.mapper = mapper;
	}

	@Override
	public Mono<AuditEntry> append(AuditEntry entry) {
		return template.insert(mapper.toEntity(entry)).map(mapper::toDomain);
	}

}
