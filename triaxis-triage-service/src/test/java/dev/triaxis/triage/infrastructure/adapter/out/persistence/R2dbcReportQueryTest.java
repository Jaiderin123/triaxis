package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.HourlyLoad;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.ReportRange;
import dev.triaxis.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report SQL against a real PostgreSQL with known waits. The patients live in an epoch of their own in the
 * past, so the patients of other tests are never in the range. The test JVM is not in UTC, the session is.
 */
@TriageIntegrationTest
class R2dbcReportQueryTest {

	@Autowired
	R2dbcPatientRepository patients;

	@Autowired
	R2dbcReportQuery reports;

	private Instant h0;
	private ReportRange range;

	private void save(Instant registeredAt, TriageLevel level, PatientStatus status, Integer waitMinutes) {
		Patient fresh = PersistenceTestSupport.newPatient();
		Instant consultation = waitMinutes == null ? null : registeredAt.plus(Duration.ofMinutes(waitMinutes));
		Instant changed = consultation != null ? consultation
				: status == PatientStatus.DISCHARGED ? registeredAt.plusSeconds(600) : registeredAt;
		patients.save(new Patient(fresh.id(), fresh.fullName(), fresh.age(), fresh.vitalSigns(), fresh.chiefComplaint(),
				level, 5, status, false, null, registeredAt, changed, consultation)).block();
	}

	@BeforeEach
	void dataset() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		h0 = epoch.plus(Duration.ofHours(10));
		range = new ReportRange(h0, h0.plus(Duration.ofHours(4)));
		// Hour 0: three waits (10, 20, 30), one discharged directly, one still waiting.
		save(h0, TriageLevel.CRITICAL, PatientStatus.IN_CONSULTATION, 10);
		save(h0.plusSeconds(600), TriageLevel.CRITICAL, PatientStatus.DISCHARGED, 20);
		save(h0.plusSeconds(1200), TriageLevel.URGENT, PatientStatus.IN_CONSULTATION, 30);
		save(h0.plusSeconds(1800), TriageLevel.NOT_URGENT, PatientStatus.DISCHARGED, null);
		save(h0.plusSeconds(2400), TriageLevel.NOT_URGENT_2, PatientStatus.REGISTERED, null);
		// Hour 1: nobody. Hour 2: one patient discharged directly.
		save(h0.plus(Duration.ofHours(2)), TriageLevel.URGENT_2, PatientStatus.DISCHARGED, null);
		// Hour 3: five patients (a tie with hour 0), waits 5 and 15.
		Instant h3 = h0.plus(Duration.ofHours(3));
		save(h3, TriageLevel.URGENT, PatientStatus.IN_CONSULTATION, 5);
		save(h3.plusSeconds(60), TriageLevel.URGENT, PatientStatus.IN_CONSULTATION, 15);
		for (int i = 0; i < 3; i++) {
			save(h3.plusSeconds(120 + i), TriageLevel.NOT_URGENT, PatientStatus.REGISTERED, null);
		}
		// Outside the range: at its end (excluded) and just before its start.
		save(range.to(), TriageLevel.CRITICAL, PatientStatus.REGISTERED, null);
		save(h0.minusSeconds(60), TriageLevel.CRITICAL, PatientStatus.REGISTERED, null);
	}

	private List<HourlyLoad> report(ReportRange of) {
		return reports.hourlyLoads(of).collectList().block();
	}

	@Test
	void groupsByUtcHourAndOmitsHoursWithoutPatients() {
		assertThat(report(range)).extracting(HourlyLoad::hour)
				.containsExactly(h0, h0.plus(Duration.ofHours(2)), h0.plus(Duration.ofHours(3)));
	}

	@Test
	void countsPerLevelIncludeEveryPatientOfTheHour() {
		HourlyLoad first = report(range).get(0);

		assertThat(first.total()).isEqualTo(5);
		assertThat(first.byLevel()).containsEntry(TriageLevel.CRITICAL, 2L).containsEntry(TriageLevel.URGENT, 1L)
				.containsEntry(TriageLevel.URGENT_2, 0L).containsEntry(TriageLevel.NOT_URGENT, 1L)
				.containsEntry(TriageLevel.NOT_URGENT_2, 1L);
	}

	@Test
	void waitsOnlyCountPatientsWhoEnteredConsultation() {
		List<HourlyLoad> hours = report(range);

		assertThat(hours.get(0).averageWaitMinutes()).isEqualByComparingTo(new BigDecimal("20.00"));
		assertThat(hours.get(0).p95WaitMinutes()).isEqualByComparingTo(new BigDecimal("29.00"));
		assertThat(hours.get(2).averageWaitMinutes()).isEqualByComparingTo(new BigDecimal("10.00"));
		assertThat(hours.get(2).p95WaitMinutes()).isEqualByComparingTo(new BigDecimal("14.50"));
	}

	@Test
	void anHourWhereNobodyEnteredConsultationHasNoWaits() {
		HourlyLoad second = report(range).get(1);

		assertThat(second.total()).isEqualTo(1);
		assertThat(second.averageWaitMinutes()).isNull();
		assertThat(second.p95WaitMinutes()).isNull();
	}

	@Test
	void runningTotalAndRankWithATie() {
		List<HourlyLoad> hours = report(range);

		assertThat(hours).extracting(HourlyLoad::cumulativeTotal).containsExactly(5L, 6L, 11L);
		assertThat(hours).extracting(HourlyLoad::loadRank).containsExactly(1, 3, 1);
	}

	@Test
	void theEndOfTheRangeIsExcludedAndTheStartIncluded() {
		assertThat(report(range).stream().mapToLong(HourlyLoad::total).sum()).isEqualTo(11);
		assertThat(report(new ReportRange(h0, h0.plusSeconds(1))))
				.singleElement().satisfies(load -> assertThat(load.total()).isEqualTo(1));
	}

	@Test
	void aRangeWithoutPatientsIsEmpty() {
		Instant empty = h0.minus(Duration.ofDays(1));
		assertThat(report(new ReportRange(empty, empty.plus(Duration.ofHours(6))))).isEmpty();
	}

}
