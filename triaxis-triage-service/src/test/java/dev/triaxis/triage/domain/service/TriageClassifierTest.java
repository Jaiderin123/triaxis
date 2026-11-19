package dev.triaxis.triage.domain.service;

import java.util.function.UnaryOperator;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TriageClassifierTest {

	private static TriageLevel levelOf(RegistrationData data) {
		return Samples.register(data).level();
	}

	private static TriageLevel levelOf(UnaryOperator<Samples.Builder> change) {
		return levelOf(Samples.with(change));
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("Reference patients of the requirements")
	@CsvSource({
			"E1, 130, 180, 110, 97, 36.8, 8, 15, Dolor toracico, CRITICAL",
			"E2, 95, 150, 95, 91, 37.0, 4, 15, Dificultad para respirar, URGENT",
			"E3, 88, 130, 85, 98, 38.2, 6, 15, Fiebre, URGENT_2",
			"E4, 72, 120, 80, 99, 36.5, 2, 15, Herida por cortar, NOT_URGENT",
			"E5, 70, 115, 75, 99, 36.4, 0, 15, Renovación de recetario, NOT_URGENT_2",
			"E6, 35, 80, 50, 85, 35.9, 0, 6, Inconsciente, CRITICAL"
	})
	void referencePatients(String id, int hr, int sys, int dia, int spo2, String temp, int pain, int gcs,
			String complaint, TriageLevel expected) {
		RegistrationData data = Samples.with(b -> b.heartRate(hr).systolic(sys).diastolic(dia).spo2(spo2)
				.temperature(temp).pain(pain).gcs(gcs).complaint(complaint));

		assertThat(levelOf(data)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "HR {0} is {1}")
	@CsvSource({"39, CRITICAL", "40, NOT_URGENT", "89, NOT_URGENT", "90, URGENT_2", "120, URGENT_2", "121, CRITICAL"})
	void heartRateBoundaries(int heartRate, TriageLevel expected) {
		assertThat(levelOf(b -> b.heartRate(heartRate))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "SpO2 {0} is {1}")
	@CsvSource({"89, CRITICAL", "90, URGENT", "93, URGENT", "94, NOT_URGENT"})
	void spo2Boundaries(int spo2, TriageLevel expected) {
		assertThat(levelOf(b -> b.spo2(spo2))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "systolic {0} is {1}")
	@CsvSource({"89, CRITICAL", "90, URGENT", "100, URGENT", "101, NOT_URGENT"})
	void systolicBoundaries(int systolic, TriageLevel expected) {
		assertThat(levelOf(b -> b.systolic(systolic).diastolic(60))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "temperature {0} is {1}")
	@CsvSource({"37.9, NOT_URGENT", "38.0, URGENT_2", "38.4, URGENT_2", "38.5, URGENT"})
	void temperatureBoundaries(String temperature, TriageLevel expected) {
		assertThat(levelOf(b -> b.temperature(temperature))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "GCS {0} is {1}")
	@CsvSource({"8, CRITICAL", "9, URGENT", "13, URGENT", "14, NOT_URGENT", "15, NOT_URGENT"})
	void gcsBoundaries(int gcs, TriageLevel expected) {
		assertThat(levelOf(b -> b.gcs(gcs))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "pain {0} is {1}")
	@CsvSource({"6, NOT_URGENT", "7, URGENT_2", "10, URGENT_2"})
	void painBoundaries(int pain, TriageLevel expected) {
		assertThat(levelOf(b -> b.pain(pain))).isEqualTo(expected);
	}

	@Test
	void mostSevereLevelWinsWhenSeveralConditionsMatch() {
		assertThat(levelOf(b -> b.heartRate(130).spo2(91).temperature("38.2"))).isEqualTo(TriageLevel.CRITICAL);
		assertThat(levelOf(b -> b.spo2(91).temperature("38.2").pain(8))).isEqualTo(TriageLevel.URGENT);
	}

	@Test
	void administrativeComplaintOnlyDowngradesLevelFour() {
		assertThat(levelOf(b -> b.complaint("Vacunación"))).isEqualTo(TriageLevel.NOT_URGENT_2);
		assertThat(levelOf(b -> b.complaint("Vacunación").spo2(91))).isEqualTo(TriageLevel.URGENT);
		assertThat(levelOf(b -> b.complaint("Seguimiento").pain(8))).isEqualTo(TriageLevel.URGENT_2);
	}

	@Test
	void clinicalComplaintThatMentionsAnAdministrativeWordIsNotDowngraded() {
		assertThat(levelOf(b -> b.complaint("Seguimiento de dolor toracico"))).isEqualTo(TriageLevel.NOT_URGENT);
	}

	@ParameterizedTest
	@CsvSource({"documentos", "DOCUMENTOS", "  Documentos  ", "Recolección de resultados",
			"recoleccion   de resultados", "Renovación de recetario", "VACUNACION"})
	void complaintMatchingIgnoresCaseAccentsAndSpaces(String complaint) {
		assertThat(levelOf(b -> b.complaint(complaint))).isEqualTo(TriageLevel.NOT_URGENT_2);
	}

	@Test
	void registeredPatientCarriesSlaStatusAndTimestamps() {
		Patient patient = Samples.register(Samples.with(b -> b.heartRate(130)));

		assertThat(patient.level()).isEqualTo(TriageLevel.CRITICAL);
		assertThat(patient.slaMinutes()).isEqualTo(5);
		assertThat(patient.status().name()).isEqualTo("REGISTERED");
		assertThat(patient.escalated()).isFalse();
		assertThat(patient.registeredAt()).isEqualTo(Samples.NOW);
		assertThat(patient.statusChangedAt()).isEqualTo(Samples.NOW);
		assertThat(patient.consultationStartedAt()).isNull();
	}

	@ParameterizedTest(name = "{0} has an SLA of {1} minutes")
	@CsvSource({"CRITICAL, 5", "URGENT, 10", "URGENT_2, 30", "NOT_URGENT, 60", "NOT_URGENT_2, 120"})
	void slaPerLevel(TriageLevel level, int minutes) {
		assertThat(Samples.sla().minutesFor(level)).isEqualTo(minutes);
	}

}
