package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.EscalationAlert;
import reactor.core.publisher.Mono;

/**
 * Channel that delivers an alert to the medical team. An implementation that calls a remote service bounds the
 * call with timeouts and signals an error when the channel did not accept the alert; it never logs the content.
 */
public interface AlertNotifier {

	Mono<Void> send(EscalationAlert alert);

}
