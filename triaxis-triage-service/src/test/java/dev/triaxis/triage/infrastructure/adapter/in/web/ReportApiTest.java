package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.doctor;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;

/** {@code GET /triage/reportes/operacion}: contract, range parameters, errors and roles. */
@TriageIntegrationTest
class ReportApiTest {

	@Autowired
	WebTestClient client;

	@Autowired
	PatientRepository patients;

	private LocalDate day;
	private Instant noon;

	@BeforeEach
	void twoPatientsAtNoon() {
		day = LocalDate.ofInstant(PersistenceTestSupport.nextEpoch(), ZoneOffset.UTC);
		noon = day.atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(12));
		save(noon.plusSeconds(60), TriageLevel.CRITICAL, 15);
		save(noon.plusSeconds(120), TriageLevel.NOT_URGENT_2, null);
	}

	private void save(Instant registeredAt, TriageLevel level, Integer waitMinutes) {
		Patient fresh = PersistenceTestSupport.newPatient();
		Instant consultation = waitMinutes == null ? null : registeredAt.plus(Duration.ofMinutes(waitMinutes));
		patients.save(new Patient(fresh.id(), fresh.fullName(), fresh.age(), fresh.vitalSigns(), fresh.chiefComplaint(),
				level, 5, consultation == null ? PatientStatus.REGISTERED : PatientStatus.IN_CONSULTATION, false, null,
				registeredAt, consultation == null ? registeredAt : consultation, consultation)).block();
	}

	private WebTestClient.ResponseSpec get(String bearer, String query) {
		WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/triage/reportes/operacion" + query);
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, bearer);
		}
		return request.exchange();
	}

	@Test
	void aWholeDayFromDatesWithTheContractInSpanish() {
		get(doctor(), "?desde=" + day + "&hasta=" + day).expectStatus().isOk()
				.expectBody()
				.jsonPath("$.desde").isEqualTo(day + "T00:00:00Z")
				.jsonPath("$.hasta").isEqualTo(day.plusDays(1) + "T00:00:00Z")
				.jsonPath("$.horas.length()").isEqualTo(1)
				.jsonPath("$.horas[0].hora").isEqualTo(day + "T12:00:00Z")
				.jsonPath("$.horas[0].totalPacientes").isEqualTo(2)
				.jsonPath("$.horas[0].porNivel.CRITICO").isEqualTo(1)
				.jsonPath("$.horas[0].porNivel.URGENTE").isEqualTo(0)
				.jsonPath("$.horas[0].porNivel.NO_URGENTE_2").isEqualTo(1)
				.jsonPath("$.horas[0].esperaPromedioMinutos").isEqualTo(15.0)
				.jsonPath("$.horas[0].esperaP95Minutos").isEqualTo(15.0)
				.jsonPath("$.horas[0].totalAcumulado").isEqualTo(2)
				.jsonPath("$.horas[0].rankingCarga").isEqualTo(1);
	}

	@Test
	void instantsWithAnOffsetAreAccepted() {
		// 07:00-05:00 is 12:00Z, so the range starts at noon UTC.
		get(doctor(), "?desde=" + day + "T07:00:00-05:00&hasta=" + day + "T13:00:00Z").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.desde").isEqualTo(day + "T12:00:00Z")
				.jsonPath("$.horas.length()").isEqualTo(1);
	}

	@Test
	void aRangeWithoutPatientsHasNoHours() {
		get(doctor(), "?desde=" + day.minusDays(1) + "&hasta=" + day.minusDays(1)).expectStatus().isOk()
				.expectBody().jsonPath("$.horas.length()").isEqualTo(0);
	}

	@Test
	void aMissingParameterIsABadRequestNamingIt() {
		get(doctor(), "?hasta=" + day).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("desde");
		get(doctor(), "").expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores.length()").isEqualTo(2);
	}

	@Test
	void malformedAndZoneLessValuesAreBadRequests() {
		get(doctor(), "?desde=yesterday&hasta=" + day).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("desde");
		get(doctor(), "?desde=" + day + "&hasta=" + day + "T13:00:00").expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.errores[0].campo").isEqualTo("hasta")
				.jsonPath("$.errores[0].mensaje").value(message -> org.assertj.core.api.Assertions
						.assertThat((String) message).contains("time zone"));
	}

	@Test
	void anInvertedOrTooLongRangeIsABadRequestOnHasta() {
		get(doctor(), "?desde=" + day + "&hasta=" + day.minusDays(2)).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("hasta");
		get(doctor(), "?desde=" + day + "&hasta=" + day.plusDays(31)).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].mensaje").value(message -> org.assertj.core.api.Assertions
						.assertThat((String) message).contains("31 days"));
		get(doctor(), "?desde=" + day + "&hasta=" + day.plusDays(30)).expectStatus().isOk();
	}

	@Test
	void onlyDoctorsReadTheReport() {
		String query = "?desde=" + day + "&hasta=" + day;
		get(null, query).expectStatus().isUnauthorized();
		get(nurse(), query).expectStatus().isForbidden();
		get(doctor(), query).expectStatus().isOk();
	}

}
