package dev.triaxis.assistant.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Answer of {@code GET /asistente/estado-urgencias} (ASSUMPTIONS A35): active patients for each of the five
 * levels, in priority order, their total and the time of the newest applied event (null when none).
 */
public record EstadoUrgenciasResponse(Map<String, Long> porNivel, long total, Instant actualizadoEn) {
}
