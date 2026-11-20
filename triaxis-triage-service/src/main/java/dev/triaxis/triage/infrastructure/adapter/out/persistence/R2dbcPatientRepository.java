package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.entity.PatientEntity;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.mapper.PatientEntityMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

@Repository
class R2dbcPatientRepository implements PatientRepository {

	private static final String UPDATE_STATUS = """
			UPDATE patients
			SET status = :next,
			    status_changed_at = :now,
			    consultation_started_at = CASE WHEN :next = 'IN_CONSULTATION' THEN :now ELSE consultation_started_at END
			WHERE id = :id AND status = :expected
			RETURNING *
			""";

	/**
	 * One atomic statement (ARCHITECTURE 6.6, ADR 0006). Rows locked by a concurrent status change are skipped,
	 * and the outer conditions are checked again on the locked rows, so a patient is escalated at most once and
	 * never after it left {@code REGISTERED}. The limit is strict (ASSUMPTIONS A6).
	 */
	private static final String ESCALATE_OVERDUE = """
			WITH overdue AS (
			  SELECT id FROM patients
			  WHERE status = 'REGISTERED'
			    AND NOT escalated
			    AND registered_at + make_interval(mins => sla_minutes) < :now
			  ORDER BY registered_at, id
			  LIMIT :limit
			  FOR UPDATE SKIP LOCKED
			)
			UPDATE patients p
			SET escalated = TRUE, escalated_at = :now
			FROM overdue o
			WHERE p.id = o.id AND p.status = 'REGISTERED' AND NOT p.escalated
			RETURNING p.*
			""";

	private final R2dbcEntityTemplate template;
	private final DatabaseClient client;
	private final R2dbcConverter converter;
	private final PatientEntityMapper mapper;

	R2dbcPatientRepository(R2dbcEntityTemplate template, R2dbcConverter converter, PatientEntityMapper mapper) {
		this.template = template;
		this.client = template.getDatabaseClient();
		this.converter = converter;
		this.mapper = mapper;
	}

	@Override
	public Mono<Patient> save(Patient patient) {
		return template.insert(mapper.toEntity(patient)).map(mapper::toDomain);
	}

	@Override
	public Mono<Patient> findById(UUID id) {
		return template.selectOne(query(where("id").is(id)), PatientEntity.class).map(mapper::toDomain);
	}

	@Override
	public Mono<Patient> updateStatus(UUID id, PatientStatus expected, PatientStatus next, Instant now) {
		return client.sql(UPDATE_STATUS)
				.bind("id", id)
				.bind("expected", expected.name())
				.bind("next", next.name())
				.bind("now", now.atOffset(ZoneOffset.UTC))
				.map((row, metadata) -> converter.read(PatientEntity.class, row, metadata))
				.one()
				.map(mapper::toDomain);
	}

	@Override
	public Flux<Patient> escalateOverdue(Instant now, int limit) {
		return client.sql(ESCALATE_OVERDUE)
				.bind("now", now.atOffset(ZoneOffset.UTC))
				.bind("limit", limit)
				.map((row, metadata) -> converter.read(PatientEntity.class, row, metadata))
				.all()
				.map(mapper::toDomain)
				// UPDATE ... RETURNING gives no order; callers get the oldest first.
				.sort(java.util.Comparator.comparing(Patient::registeredAt).thenComparing(Patient::id));
	}

}
