package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.dto.LoginRequest;
import com.orbita.api.auth.internal.dto.TokenResponse;
import com.orbita.api.auth.internal.model.User;
import com.orbita.api.auth.internal.repository.UserRepository;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public TokenResponse login(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(normalizedEmail)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new OrbitaApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
		}

		if (!user.isEmailVerified()) {
			throw new OrbitaApiException(ErrorCode.EMAIL_NOT_VERIFIED, HttpStatus.FORBIDDEN);
		}

		String accessToken = jwtService.generateAccessToken(user.getId());
		return new TokenResponse(accessToken);
	}

}
