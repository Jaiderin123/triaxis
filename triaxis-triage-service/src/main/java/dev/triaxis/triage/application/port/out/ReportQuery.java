package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.HourlyLoad;
import dev.triaxis.triage.domain.model.ReportRange;
import reactor.core.publisher.Flux;

/**
 * Computes the hourly loads of a range in the database, in one query (RF-05 forbids aggregating in memory).
 * Hours without patients are not returned; the result is ordered by hour.
 */
public interface ReportQuery {

	Flux<HourlyLoad> hourlyLoads(ReportRange range);

}
