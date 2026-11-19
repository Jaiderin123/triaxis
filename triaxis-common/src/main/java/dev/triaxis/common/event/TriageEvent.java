package dev.triaxis.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Message published to {@code triaje.eventos}. Every event carries the level of the patient so consumers
 * can count by level without calling the triage service. {@code estadoAnterior} is null for the registration.
 * Delivery is at least once: consumers must be idempotent ({@code idEvento} identifies a message).
 *
 * @param idEvento       unique id of this event
 * @param tipo           event name
 * @param version        schema version of the message, currently {@link #CURRENT_VERSION}
 * @param ocurridoEn     when the change happened (UTC)
 * @param pacienteId     patient the event refers to (also the Kafka key)
 * @param nivel          triage level of the patient
 * @param estadoAnterior status before the change, or null
 * @param estadoNuevo    status after the change
 * @param slaMinutos     SLA of the patient in minutes
 * @param escalado       whether the patient has been escalated
 */
public record TriageEvent(
		UUID idEvento,
		EventType tipo,
		int version,
		Instant ocurridoEn,
		UUID pacienteId,
		EventLevel nivel,
		EventStatus estadoAnterior,
		EventStatus estadoNuevo,
		int slaMinutos,
		boolean escalado) {

	public static final int CURRENT_VERSION = 1;

	/** Kafka topic of the contract. */
	public static final String TOPIC = "triaje.eventos";

	/** Kafka header with the correlation id of the request that caused the event. */
	public static final String CORRELATION_ID_HEADER = "correlationId";

	/** Kafka header with the event name (same value as {@code tipo}). */
	public static final String EVENT_TYPE_HEADER = "eventType";

}
