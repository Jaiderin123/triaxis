package dev.triaxis.triage.domain.service;

import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusTransitionPolicyTest {

	private final StatusTransitionPolicy policy = new StatusTransitionPolicy();

	@ParameterizedTest(name = "{0} to {1} is allowed")
	@CsvSource({"REGISTERED, IN_CONSULTATION", "REGISTERED, DISCHARGED", "IN_CONSULTATION, DISCHARGED"})
	void allowsTheValidTransitions(PatientStatus from, PatientStatus to) {
		assertThatCode(() -> policy.ensureAllowed(from, to)).doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "{0} to {1} is rejected")
	@CsvSource({
			"REGISTERED, REGISTERED",
			"IN_CONSULTATION, REGISTERED",
			"IN_CONSULTATION, IN_CONSULTATION",
			"DISCHARGED, REGISTERED",
			"DISCHARGED, IN_CONSULTATION",
			"DISCHARGED, DISCHARGED"
	})
	void rejectsTheRest(PatientStatus from, PatientStatus to) {
		assertThatThrownBy(() -> policy.ensureAllowed(from, to))
				.isInstanceOf(InvalidStatusTransitionException.class)
				.hasMessageContaining(from.name())
				.hasMessageContaining(to.name());
	}

	@Test
	void dischargedPatientHasNoWayOut() {
		assertThat(PatientStatus.DISCHARGED.allowedTransitions()).isEmpty();
		assertThatThrownBy(() -> policy.ensureAllowed(PatientStatus.DISCHARGED, PatientStatus.IN_CONSULTATION))
				.hasMessageContaining("none");
	}

}
