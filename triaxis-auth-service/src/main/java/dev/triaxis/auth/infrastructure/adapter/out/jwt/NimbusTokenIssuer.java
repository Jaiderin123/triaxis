package dev.triaxis.auth.infrastructure.adapter.out.jwt;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.triaxis.auth.application.port.out.TokenIssuer;
import dev.triaxis.auth.domain.model.IssuedToken;
import dev.triaxis.auth.domain.model.Role;
import dev.triaxis.auth.domain.model.User;
import dev.triaxis.auth.infrastructure.config.AuthProperties;
import org.springframework.stereotype.Component;

/** Signs RS256 tokens with {@code sub}, {@code roles}, {@code iss}, {@code iat}, {@code exp} and {@code jti}. */
@Component
class NimbusTokenIssuer implements TokenIssuer {

	private final SigningKeyProvider keys;
	private final String issuer;
	private final String audience;
	private final Duration ttl;

	NimbusTokenIssuer(SigningKeyProvider keys, AuthProperties properties) {
		this.keys = keys;
		this.issuer = properties.jwt().issuer();
		this.audience = properties.jwt().audience();
		this.ttl = properties.jwt().ttl();
	}

	@Override
	public IssuedToken issue(User user, java.time.Instant issuedAt) {
		List<String> roles = user.roles().stream().map(Role::name).sorted().toList();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(user.username())
				.issuer(issuer)
				.audience(audience)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plus(ttl)))
				.jwtID(UUID.randomUUID().toString())
				.claim("roles", roles)
				.build();
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.signingKey().getKeyID()).build();
		try {
			SignedJWT jwt = new SignedJWT(header, claims);
			jwt.sign(new RSASSASigner(keys.signingKey()));
			return new IssuedToken(jwt.serialize(), ttl.toSeconds());
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Could not sign the token", e);
		}
	}

}
