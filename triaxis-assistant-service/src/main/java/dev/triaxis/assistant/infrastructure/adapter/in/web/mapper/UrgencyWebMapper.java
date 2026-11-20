package dev.triaxis.assistant.infrastructure.adapter.in.web.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyView;
import dev.triaxis.assistant.infrastructure.adapter.in.web.dto.EstadoUrgenciasResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;

/** Copies the urgency view to the Spanish contract, keeping the levels in priority order. */
@Mapper
public interface UrgencyWebMapper {

	@ValueMapping(source = "CRITICAL", target = "CRITICO")
	@ValueMapping(source = "URGENT", target = "URGENTE")
	@ValueMapping(source = "URGENT_2", target = "URGENTE_2")
	@ValueMapping(source = "NOT_URGENT", target = "NO_URGENTE")
	@ValueMapping(source = "NOT_URGENT_2", target = "NO_URGENTE_2")
	String toNivel(TriageLevel level);

	default EstadoUrgenciasResponse toResponse(UrgencyView view) {
		Map<String, Long> byLevel = new LinkedHashMap<>();
		view.active().forEach((level, count) -> byLevel.put(toNivel(level), count));
		return new EstadoUrgenciasResponse(byLevel, view.total(), view.updatedAt());
	}

}
