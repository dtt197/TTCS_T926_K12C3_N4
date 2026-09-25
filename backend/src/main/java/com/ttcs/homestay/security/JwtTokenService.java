package com.ttcs.homestay.security;

import com.ttcs.homestay.config.JwtProperties;
import com.ttcs.homestay.dto.auth.RefreshResponse;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidRefreshTokenException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

	public record IssuedTokens(
			String accessToken,
			String refreshToken
	) {
	}

	private static final String ACCESS_TYPE = "access";
	private static final String REFRESH_TYPE = "refresh";
	private static final String ACCESS_AUDIENCE = "homestay-api";
	private static final String REFRESH_AUDIENCE = "homestay-refresh";

	private final JwtProperties properties;
	private final Clock clock;
	private final JwtEncoder accessEncoder;
	private final JwtEncoder refreshEncoder;
	private final JwtDecoder refreshDecoder;

	@Autowired
	public JwtTokenService(JwtProperties properties) {
		this(properties, Clock.systemUTC());
	}

	JwtTokenService(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.accessEncoder = encoder(properties.getAccessSecret());
		this.refreshEncoder = encoder(properties.getRefreshSecret());
		this.refreshDecoder = decoder(properties.getRefreshSecret());
	}

	public IssuedTokens issueTokens(User user) {
		Instant issuedAt = clock.instant();
		String accessToken = accessToken(user, issuedAt);
		String refreshToken = refreshToken(user, issuedAt);
		return new IssuedTokens(accessToken, refreshToken);
	}

	public long accessTtlSeconds() {
		return properties.getAccessTtl().toSeconds();
	}

	public RefreshResponse refreshAccessToken(String token) {
		try {
			Jwt refresh = refreshDecoder.decode(token);
			if (!REFRESH_TYPE.equals(refresh.getClaimAsString("typ"))
					|| !refresh.getAudience().contains(REFRESH_AUDIENCE)) {
				throw new InvalidRefreshTokenException();
			}

			Instant issuedAt = clock.instant();
			JwtClaimsSet claims = JwtClaimsSet.builder()
					.issuer("homestay")
					.subject(refresh.getSubject())
					.audience(List.of(ACCESS_AUDIENCE))
					.issuedAt(issuedAt)
					.expiresAt(issuedAt.plus(properties.getAccessTtl()))
					.id(UUID.randomUUID().toString())
					.claim("typ", ACCESS_TYPE)
					.claim("email", refresh.getClaimAsString("email"))
					.claim("role", refresh.getClaimAsString("role"))
					.build();
			return new RefreshResponse(encode(accessEncoder, claims), properties.getAccessTtl().toSeconds());
		} catch (InvalidRefreshTokenException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new InvalidRefreshTokenException();
		}
	}

	private String accessToken(User user, Instant issuedAt) {
		JwtClaimsSet claims = baseClaims(user, issuedAt, ACCESS_AUDIENCE, ACCESS_TYPE, properties.getAccessTtl());
		return encode(accessEncoder, claims);
	}

	private String refreshToken(User user, Instant issuedAt) {
		JwtClaimsSet claims = baseClaims(user, issuedAt, REFRESH_AUDIENCE, REFRESH_TYPE, properties.getRefreshTtl());
		return encode(refreshEncoder, claims);
	}

	private JwtClaimsSet baseClaims(User user, Instant issuedAt, String audience, String type, Duration ttl) {
		return JwtClaimsSet.builder()
				.issuer("homestay")
				.subject(user.getId().toString())
				.audience(List.of(audience))
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(ttl))
				.id(UUID.randomUUID().toString())
				.claim("typ", type)
				.claim("fullName", user.getFullName())
				.claim("email", user.getEmail())
				.claim("role", user.getRole().getCode())
				.build();
	}

	private JwtEncoder encoder(String secret) {
		return NimbusJwtEncoder.withSecretKey(secretKey(secret))
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	private JwtDecoder decoder(String secret) {
		return NimbusJwtDecoder.withSecretKey(secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private String encode(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(jwtParameters(claims)).getTokenValue();
	}

	private SecretKey secretKey(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private JwtEncoderParameters jwtParameters(JwtClaimsSet claims) {
		return JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
	}
}
