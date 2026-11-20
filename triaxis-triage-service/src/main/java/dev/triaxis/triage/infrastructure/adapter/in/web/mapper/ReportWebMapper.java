package dev.triaxis.triage.infrastructure.adapter.in.web.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.triaxis.triage.domain.model.HourlyLoad;
import dev.triaxis.triage.domain.model.OperationReport;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.HourResponse;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.OperationReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Copies the operation report to the Spanish contract; the levels keep their priority order. */
@Mapper
public interface ReportWebMapper {

	@Mapping(target = "desde", source = "range.from")
	@Mapping(target = "hasta", source = "range.to")
	@Mapping(target = "horas", source = "hours")
	OperationReportResponse toResponse(OperationReport report);

	@Mapping(target = "hora", source = "hour")
	@Mapping(target = "totalPacientes", source = "total")
	@Mapping(target = "porNivel", source = "byLevel")
	@Mapping(target = "esperaPromedioMinutos", source = "averageWaitMinutes")
	@Mapping(target = "esperaP95Minutos", source = "p95WaitMinutes")
	@Mapping(target = "totalAcumulado", source = "cumulativeTotal")
	@Mapping(target = "rankingCarga", source = "loadRank")
	HourResponse toHour(HourlyLoad load);

	default Map<String, Long> toLevels(Map<TriageLevel, Long> byLevel) {
		Map<String, Long> levels = new LinkedHashMap<>();
		for (TriageLevel level : TriageLevel.values()) {
			levels.put(ExternalValues.level(level), byLevel.getOrDefault(level, 0L));
		}
		return levels;
	}

}
