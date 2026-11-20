package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import dev.triaxis.triage.application.port.out.ReportQuery;
import dev.triaxis.triage.domain.model.HourlyLoad;
import dev.triaxis.triage.domain.model.ReportRange;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.infrastructure.config.ReportProperties;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * The operation report in one SQL statement (RF-05, ARCHITECTURE 6.7): the patients of the range are grouped by
 * UTC hour of registration with filtered aggregates per level, the mean and the 95th percentile of the wait of
 * those who entered consultation, and then window functions give the running total and the load rank. Nothing is
 * aggregated in memory. The session is UTC (ADR 0022), so {@code date_trunc} gives UTC hours.
 */
@Repository
class R2dbcReportQuery implements ReportQuery {

	static final String REPORT = """
			WITH waits AS (
			  SELECT date_trunc('hour', registered_at) AS hour,
			         level,
			         extract(epoch FROM consultation_started_at - registered_at)::double precision / 60 AS wait_min
			  FROM patients
			  WHERE registered_at >= :from AND registered_at < :to
			),
			hourly AS (
			  SELECT hour,
			         count(*)                                       AS total,
			         count(*) FILTER (WHERE level = 'CRITICAL')     AS critical,
			         count(*) FILTER (WHERE level = 'URGENT')       AS urgent,
			         count(*) FILTER (WHERE level = 'URGENT_2')     AS urgent_2,
			         count(*) FILTER (WHERE level = 'NOT_URGENT')   AS not_urgent,
			         count(*) FILTER (WHERE level = 'NOT_URGENT_2') AS not_urgent_2,
			         round(avg(wait_min) FILTER (WHERE wait_min IS NOT NULL)::numeric, 2) AS avg_wait_min,
			         round((percentile_cont(0.95) WITHIN GROUP (ORDER BY wait_min)
			                FILTER (WHERE wait_min IS NOT NULL))::numeric, 2)           AS p95_wait_min
			  FROM waits
			  GROUP BY hour
			)
			SELECT hour, total, critical, urgent, urgent_2, not_urgent, not_urgent_2, avg_wait_min, p95_wait_min,
			       (sum(total) OVER (ORDER BY hour))::bigint  AS cumulative_total,
			       (rank() OVER (ORDER BY total DESC))::int  AS load_rank
			FROM hourly
			ORDER BY hour
			""";

	private final DatabaseClient client;
	private final ReportProperties properties;

	R2dbcReportQuery(DatabaseClient client, ReportProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public Flux<HourlyLoad> hourlyLoads(ReportRange range) {
		return client.sql(REPORT)
				.bind("from", range.from().atOffset(ZoneOffset.UTC))
				.bind("to", range.to().atOffset(ZoneOffset.UTC))
				.map(R2dbcReportQuery::toLoad)
				.all()
				.timeout(properties.timeout());
	}

	private static HourlyLoad toLoad(Readable row) {
		Map<TriageLevel, Long> byLevel = new EnumMap<>(TriageLevel.class);
		byLevel.put(TriageLevel.CRITICAL, row.get("critical", Long.class));
		byLevel.put(TriageLevel.URGENT, row.get("urgent", Long.class));
		byLevel.put(TriageLevel.URGENT_2, row.get("urgent_2", Long.class));
		byLevel.put(TriageLevel.NOT_URGENT, row.get("not_urgent", Long.class));
		byLevel.put(TriageLevel.NOT_URGENT_2, row.get("not_urgent_2", Long.class));
		return new HourlyLoad(row.get("hour", OffsetDateTime.class).toInstant(), row.get("total", Long.class), byLevel,
				row.get("avg_wait_min", BigDecimal.class), row.get("p95_wait_min", BigDecimal.class),
				row.get("cumulative_total", Long.class), row.get("load_rank", Integer.class));
	}

}
