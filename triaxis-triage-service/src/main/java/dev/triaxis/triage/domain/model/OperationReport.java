package dev.triaxis.triage.domain.model;

import java.util.List;
import java.util.Objects;

/** The operation report of a range: one entry per UTC hour with patients, in order (hours without any are omitted, A8). */
public record OperationReport(ReportRange range, List<HourlyLoad> hours) {

	public OperationReport {
		Objects.requireNonNull(range, "range");
		hours = List.copyOf(hours);
	}

}
