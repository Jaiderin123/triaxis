package dev.triaxis.triage.application.port.in;

import java.time.Instant;

import dev.triaxis.triage.domain.model.OperationReport;
import reactor.core.publisher.Mono;

/** RF-05: patients and waits per UTC hour of a range. */
public interface GetOperationReportUseCase {

	/**
	 * {@code from} is included and {@code to} excluded. Fails with {@code InvalidReportRangeException} when the
	 * range is missing, empty, inverted or too long.
	 */
	Mono<OperationReport> report(Instant from, Instant to);

}
