package dev.triaxis.triage.infrastructure.adapter.out.alert;

import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAlertNotifierTest {

	private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAlertNotifier.LOGGER);
	private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

	@BeforeEach
	void capture() {
		lines.start();
		logger.addAppender(lines);
	}

	@AfterEach
	void release() {
		logger.detachAppender(lines);
	}

	@Test
	void writesOneStructuredLineWithTheMinimalContent() {
		StepVerifier.create(new LoggingAlertNotifier().send(AlertSamples.alert())).verifyComplete();

		assertThat(lines.list).singleElement().satisfies(line -> {
			assertThat(line.getFormattedMessage()).startsWith("Paciente escalado: nivel CRITICO, 12 min de espera");
			Map<String, String> pairs = line.getKeyValuePairs().stream()
					.collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
			assertThat(pairs).containsEntry("alert.tipo", "paciente_escalado")
					.containsEntry("alert.pacienteId", AlertSamples.PATIENT_ID.toString())
					.containsEntry("alert.nivel", "CRITICO")
					.containsEntry("alert.slaMinutos", "5")
					.containsEntry("alert.minutosEsperando", "12")
					.containsEntry("alert.escaladoEn", AlertSamples.ESCALATED_AT.toString())
					.containsEntry("alert.ruta", "/triage/pacientes/" + AlertSamples.PATIENT_ID);
		});
	}

	@Test
	void nothingIsWrittenBeforeSubscription() {
		new LoggingAlertNotifier().send(AlertSamples.alert());

		assertThat(lines.list).isEmpty();
	}

}
