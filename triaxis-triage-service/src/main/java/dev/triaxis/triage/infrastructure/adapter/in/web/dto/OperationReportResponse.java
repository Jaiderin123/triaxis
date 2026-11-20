package dev.triaxis.triage.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

/** Answer of {@code GET /triage/reportes/operacion} (ASSUMPTIONS A41): the range asked and its hours with patients. */
public record OperationReportResponse(Instant desde, Instant hasta, List<HourResponse> horas) {
}
