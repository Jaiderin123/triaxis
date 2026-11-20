package dev.triaxis.triage.infrastructure.adapter.out.alert;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.triage.application.port.out.AlertNotifier;
import dev.triaxis.triage.domain.model.EscalationAlert;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Posts the alert as JSON to an incoming webhook (Slack, Teams or any receiver), with connect and response
 * timeouts and an overall bound on the call. Any answer other than 2xx is an error. The URL is a secret and the
 * body holds patient data, so neither is ever part of an error or a log: failures are replaced by an
 * {@link AlertChannelException} that only names the cause.
 */
class WebhookAlertNotifier implements AlertNotifier {

	private final WebClient web;
	private final URI url;
	private final ObjectMapper json;
	private final Duration callTimeout;

	WebhookAlertNotifier(URI url, Duration connectTimeout, Duration responseTimeout, ObjectMapper json) {
		HttpClient http = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
				.responseTimeout(responseTimeout);
		this.web = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
		this.url = url;
		this.json = json;
		this.callTimeout = connectTimeout.plus(responseTimeout);
	}

	@Override
	public Mono<Void> send(EscalationAlert alert) {
		return Mono.defer(() -> {
			String body;
			try {
				body = json.writeValueAsString(AlertMessage.of(alert));
			}
			catch (JsonProcessingException e) {
				return Mono.error(new AlertChannelException("the alert could not be serialized"));
			}
			return web.post()
					.uri(url)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.toBodilessEntity()
					.timeout(callTimeout)
					.then();
		}).onErrorMap(error -> !(error instanceof AlertChannelException), WebhookAlertNotifier::sanitized);
	}

	private static AlertChannelException sanitized(Throwable error) {
		if (error instanceof WebClientResponseException response) {
			return new AlertChannelException("the webhook answered " + response.getStatusCode().value());
		}
		if (timedOut(error)) {
			return new AlertChannelException("the webhook did not answer in time");
		}
		return new AlertChannelException("the webhook call failed: " + error.getClass().getSimpleName());
	}

	/** The overall bound of Reactor, or the connect or response timeout of Netty wrapped by the client. */
	private static boolean timedOut(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof TimeoutException || cause instanceof io.netty.handler.timeout.TimeoutException
					|| cause instanceof ConnectTimeoutException) {
				return true;
			}
		}
		return false;
	}

	/** Failure of the channel whose message never holds the URL or the body. */
	static final class AlertChannelException extends RuntimeException {

		AlertChannelException(String message) {
			super(message, null, false, false);
		}

	}

}
