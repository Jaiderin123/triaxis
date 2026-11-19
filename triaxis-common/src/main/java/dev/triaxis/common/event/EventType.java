package dev.triaxis.common.event;

import com.fasterxml.jackson.annotation.JsonValue;

/** Event names of the contract. */
public enum EventType {

	PACIENTE_REGISTRADO("paciente_registrado"),
	PACIENTE_EN_CONSULTA("paciente_en_consulta"),
	PACIENTE_ALTA("paciente_alta"),
	PACIENTE_ESCALADO("paciente_escalado");

	private final String value;

	EventType(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
