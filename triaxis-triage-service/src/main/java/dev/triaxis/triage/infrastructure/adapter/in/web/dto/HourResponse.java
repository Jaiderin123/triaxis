package dev.triaxis.triage.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** One UTC hour of the operation report; the waits are null when nobody of the hour entered consultation. */
public record HourResponse(Instant hora, long totalPacientes, Map<String, Long> porNivel,
		BigDecimal esperaPromedioMinutos, BigDecimal esperaP95Minutos, long totalAcumulado, int rankingCarga) {
}
