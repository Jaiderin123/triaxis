package dev.triaxis.auth.infrastructure.config;

import dev.triaxis.auth.application.port.in.IssueTokenUseCase;
import dev.triaxis.auth.application.port.out.ClockPort;
import dev.triaxis.auth.application.port.out.PasswordVerifier;
import dev.triaxis.auth.application.port.out.TokenIssuer;
import dev.triaxis.auth.application.port.out.UserRepository;
import dev.triaxis.auth.application.service.IssueTokenService;
import dev.triaxis.auth.infrastructure.adapter.in.web.ratelimit.LoginRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class AuthConfiguration {

	@Bean
	IssueTokenUseCase issueTokenUseCase(UserRepository users, PasswordVerifier passwords, TokenIssuer tokens,
			ClockPort clock) {
		return new IssueTokenService(users, passwords, tokens, clock);
	}

	@Bean
	LoginRateLimiter loginRateLimiter(AuthProperties properties) {
		return new LoginRateLimiter(properties.rateLimit().maxAttempts(), properties.rateLimit().window(),
				java.time.Clock.systemUTC());
	}

	@Bean
	ClockPort clockPort() {
		return java.time.Instant::now;
	}

}
