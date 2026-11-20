package dev.triaxis.assistant.infrastructure.adapter.in.web.mapper;

import java.util.List;

import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.Source;
import dev.triaxis.assistant.infrastructure.adapter.in.web.dto.ConsultaResponse;
import dev.triaxis.assistant.infrastructure.adapter.in.web.dto.FuenteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Copies the checked answer to the Spanish contract; every outcome but an answer becomes the fixed notice. */
@Mapper
public interface AnswerWebMapper {

	@Mapping(target = "protocolo", source = "protocolId")
	@Mapping(target = "seccion", source = "section")
	FuenteResponse toFuente(Source source);

	List<FuenteResponse> toFuentes(List<Source> sources);

	default ConsultaResponse toResponse(ClinicalAnswer answer) {
		return switch (answer.outcome()) {
			case ANSWERED -> new ConsultaResponse(answer.text(), toFuentes(answer.sources()));
			case NO_PROTOCOL, REJECTED, DEGRADED -> ConsultaResponse.noProtocol();
		};
	}

}
