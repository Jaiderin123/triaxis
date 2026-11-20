package dev.triaxis.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.web.logging.AccessLogFilter;
import dev.triaxis.web.logging.PrincipalCaptureFilter;
import dev.triaxis.web.problem.ProblemWriter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Shared web infrastructure: the standard problem writer and the access log. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TriaxisWebAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ProblemWriter problemWriter(ObjectMapper mapper, ObjectProvider<Tracer> tracer) {
		return new ProblemWriter(mapper, tracer);
	}

	/** Outermost, so it also logs the requests that security rejects. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 10)
	AccessLogFilter accessLogFilter() {
		return new AccessLogFilter();
	}

	/** After Spring Security (whose filter runs at order -100), so the user is already known. */
	@Bean
	@Order(0)
	PrincipalCaptureFilter principalCaptureFilter() {
		return new PrincipalCaptureFilter();
	}

}
