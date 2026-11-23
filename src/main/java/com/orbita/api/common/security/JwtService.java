package com.orbita.api.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public String generateAccessToken(String userId) {
		Instant now = Instant.now();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(jwtProperties.expirationSeconds()))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
