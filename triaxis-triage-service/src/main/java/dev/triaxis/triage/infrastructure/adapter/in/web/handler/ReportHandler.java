package dev.triaxis.triage.infrastructure.adapter.in.web.handler;

import java.time.Instant;

import dev.triaxis.triage.application.port.in.GetOperationReportUseCase;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ReportWebMapper;
import dev.triaxis.triage.infrastructure.adapter.in.web.validation.ReportDateParser;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Reads the range of {@code GET /triage/reportes/operacion} and answers the report; the rules are in the domain. */
@Component
public class ReportHandler {

	private final GetOperationReportUseCase reports;
	private final ReportWebMapper mapper;

	ReportHandler(GetOperationReportUseCase reports, ReportWebMapper mapper) {
		this.reports = reports;
		this.mapper = mapper;
	}

	public Mono<ServerResponse> operation(ServerRequest request) {
		return Mono.fromCallable(() -> {
					Instant from = ReportDateParser.start(request.queryParam("desde").orElse(null), "desde");
					Instant to = ReportDateParser.end(request.queryParam("hasta").orElse(null), "hasta");
					return new Instant[] {from, to};
				})
				.flatMap(range -> reports.report(range[0], range[1]))
				.flatMap(report -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
						.bodyValue(mapper.toResponse(report)));
	}

}
