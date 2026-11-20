package dev.triaxis.triage.application.service;

import java.time.Duration;
import java.time.Instant;

import dev.triaxis.triage.application.port.in.GetOperationReportUseCase;
import dev.triaxis.triage.application.port.out.ReportQuery;
import dev.triaxis.triage.domain.model.OperationReport;
import dev.triaxis.triage.domain.model.ReportRange;
import reactor.core.publisher.Mono;

public class GetOperationReportService implements GetOperationReportUseCase {

	private final ReportQuery query;
	private final Duration maxRange;

	public GetOperationReportService(ReportQuery query, Duration maxRange) {
		if (maxRange == null || maxRange.isZero() || maxRange.isNegative()) {
			throw new IllegalArgumentException("The maximum report range must be positive");
		}
		this.query = query;
		this.maxRange = maxRange;
	}

	@Override
	public Mono<OperationReport> report(Instant from, Instant to) {
		return Mono.fromCallable(() -> ReportRange.of(from, to, maxRange))
				.flatMap(range -> query.hourlyLoads(range).collectList().map(hours -> new OperationReport(range, hours)));
	}

}
