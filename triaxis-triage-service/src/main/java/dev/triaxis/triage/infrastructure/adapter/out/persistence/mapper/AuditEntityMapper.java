package dev.triaxis.triage.infrastructure.adapter.out.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.entity.AuditEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AuditEntityMapper {

	@Mapping(target = "id", ignore = true)
	AuditEntity toEntity(AuditEntry entry);

	AuditEntry toDomain(AuditEntity entity);

	default OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	default Instant toInstant(OffsetDateTime time) {
		return time == null ? null : time.toInstant();
	}

}
