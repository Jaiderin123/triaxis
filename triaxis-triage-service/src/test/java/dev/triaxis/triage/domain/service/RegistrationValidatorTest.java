package dev.triaxis.triage.domain.service;

import java.util.List;
import java.util.function.UnaryOperator;

import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidPatientDataException;
import dev.triaxis.triage.domain.model.RegistrationData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RegistrationValidatorTest {

	private static List<String> invalidFields(UnaryOperator<Samples.Builder> change) {
		RegistrationData data = Samples.with(change);
		InvalidPatientDataException error = catchThrowableOfType(InvalidPatientDataException.class,
				() -> RegistrationValidator.validate(data));
		assertThat(error).as("expected the data to be rejected").isNotNull();
		return error.violations().stream().map(FieldViolation::field).toList();
	}

	private static void assertValid(UnaryOperator<Samples.Builder> change) {
		RegistrationData data = Samples.with(change);
		assertThatCode(() -> RegistrationValidator.validate(data)).doesNotThrowAnyException();
	}

	@Test
	void acceptsHealthyData() {
		assertThatCode(() -> RegistrationValidator.validate(Samples.healthy())).doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "heart rate {0}: {1}")
	@CsvSource({"19, false", "20, true", "250, true", "251, false"})
	void heartRateRange(int value, boolean valid) {
		check("heartRate", valid, b -> b.heartRate(value));
	}

	@ParameterizedTest(name = "systolic {0}: {1}")
	@CsvSource({"59, false", "60, true", "260, true", "261, false"})
	void systolicRange(int value, boolean valid) {
		check("systolic", valid, b -> b.systolic(value).diastolic(30));
	}

	@ParameterizedTest(name = "diastolic {0}: {1}")
	@CsvSource({"29, false", "30, true", "119, true", "160, false"})
	void diastolicRange(int value, boolean valid) {
		check("diastolic", valid, b -> b.systolic(120).diastolic(value));
	}

	@ParameterizedTest(name = "SpO2 {0}: {1}")
	@CsvSource({"69, false", "70, true", "100, true", "101, false"})
	void spo2Range(int value, boolean valid) {
		check("spo2", valid, b -> b.spo2(value));
	}

	@ParameterizedTest(name = "temperature {0}: {1}")
	@CsvSource({"33.9, false", "34.0, true", "42.0, true", "42.1, false"})
	void temperatureRange(String value, boolean valid) {
		check("temperature", valid, b -> b.temperature(value));
	}

	@ParameterizedTest(name = "pain {0}: {1}")
	@CsvSource({"-1, false", "0, true", "10, true", "11, false"})
	void painRange(int value, boolean valid) {
		check("pain", valid, b -> b.pain(value));
	}

	@ParameterizedTest(name = "GCS {0}: {1}")
	@CsvSource({"2, false", "3, true", "15, true", "16, false"})
	void gcsRange(int value, boolean valid) {
		check("gcs", valid, b -> b.gcs(value));
	}

	@ParameterizedTest(name = "age {0}: {1}")
	@CsvSource({"-1, false", "0, true", "120, true", "121, false"})
	void ageRange(int value, boolean valid) {
		check("age", valid, b -> b.age(value));
	}

	@Test
	void nameLength() {
		assertThat(invalidFields(b -> b.fullName("Al"))).containsExactly("fullName");
		assertThat(invalidFields(b -> b.fullName("A".repeat(101)))).containsExactly("fullName");
		assertThat(invalidFields(b -> b.fullName("   "))).containsExactly("fullName");
		assertValid(b -> b.fullName("Ana"));
		assertValid(b -> b.fullName("A".repeat(100)));
	}

	@Test
	void diastolicMustBeLowerThanSystolic() {
		assertThat(invalidFields(b -> b.systolic(100).diastolic(100))).containsExactly("diastolic");
		assertThat(invalidFields(b -> b.systolic(100).diastolic(120))).containsExactly("diastolic");
		assertValid(b -> b.systolic(100).diastolic(99));
	}

	@Test
	void temperatureAcceptsOnlyOneDecimal() {
		assertThat(invalidFields(b -> b.temperature("36.55"))).containsExactly("temperature");
		assertValid(b -> b.temperature("36.50"));
		assertValid(b -> b.temperature("37"));
	}

	@Test
	void chiefComplaintIsRequiredAndBounded() {
		assertThat(invalidFields(b -> b.complaint(" "))).containsExactly("chiefComplaint");
		assertThat(invalidFields(b -> b.complaint(null))).containsExactly("chiefComplaint");
		assertThat(invalidFields(b -> b.complaint("x".repeat(501)))).containsExactly("chiefComplaint");
		assertValid(b -> b.complaint("x".repeat(500)));
	}

	@Test
	void reportsMissingValues() {
		RegistrationData empty = new RegistrationData(null, null, null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> RegistrationValidator.validate(empty))
				.isInstanceOfSatisfying(InvalidPatientDataException.class, e -> assertThat(e.violations())
						.extracting(FieldViolation::field)
						.containsExactlyInAnyOrder("fullName", "chiefComplaint", "age", "heartRate", "systolic",
								"diastolic", "spo2", "pain", "gcs", "temperature"));
	}

	@Test
	void reportsEveryInvalidFieldTogetherWithAClearMessage() {
		RegistrationData data = Samples.with(b -> b.heartRate(300).spo2(50).gcs(1));

		assertThatThrownBy(() -> RegistrationValidator.validate(data))
				.isInstanceOfSatisfying(InvalidPatientDataException.class, e -> {
					assertThat(e.violations()).extracting(FieldViolation::field)
							.containsExactlyInAnyOrder("heartRate", "spo2", "gcs");
					assertThat(e.getMessage()).contains("heartRate must be between 20 and 250");
				});
	}

	private static void check(String field, boolean valid, UnaryOperator<Samples.Builder> change) {
		if (valid) {
			assertValid(change);
		}
		else {
			assertThat(invalidFields(change)).containsExactly(field);
		}
	}

}
