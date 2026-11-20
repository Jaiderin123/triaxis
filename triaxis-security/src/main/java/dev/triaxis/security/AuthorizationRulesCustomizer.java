package dev.triaxis.security;

import org.springframework.security.config.web.server.ServerHttpSecurity;

/**
 * Lets a service add its own path and role rules to the shared security filter chain.
 * Rules are applied after the public health probes and before the catch-all rule, which denies everything else:
 * every route of a service needs an explicit rule here, or it answers 401 without a token and 403 with one.
 */
@FunctionalInterface
public interface AuthorizationRulesCustomizer {

	void customize(ServerHttpSecurity.AuthorizeExchangeSpec exchanges);

}
