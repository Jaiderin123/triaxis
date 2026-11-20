package dev.triaxis.assistant.infrastructure.adapter.in.web.dto;

import java.util.List;

/** Answer of {@code POST /asistente/consulta}: the text and the protocol sections it comes from. */
public record ConsultaResponse(String respuesta, List<FuenteResponse> fuentes) {

	/** The fixed notice when no protocol applies (ASSUMPTIONS A10, A34). It never contains clinical content. */
	public static final String NO_PROTOCOL_NOTICE = "No existe un protocolo disponible para esta pregunta.";

	public static ConsultaResponse noProtocol() {
		return new ConsultaResponse(NO_PROTOCOL_NOTICE, List.of());
	}

}
