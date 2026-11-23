package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.dto.LoginRequest;
import com.orbita.api.auth.internal.dto.TokenResponse;
import com.orbita.api.auth.internal.model.User;
import com.orbita.api.auth.internal.repository.UserRepository;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	@Test
	@DisplayName("Should return accessToken on successful login for verified user")
	void shouldLoginSuccessfully() {
		User user = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.passwordHash("hashed-pw")
				.emailVerified(true)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Strong@123", "hashed-pw")).thenReturn(true);
		when(jwtService.generateAccessToken("user-123")).thenReturn("jwt-token-xyz");

		TokenResponse response = authService.login(new LoginRequest("  Alice@Example.com ", "Strong@123"));

		assertThat(response.accessToken()).isEqualTo("jwt-token-xyz");
	}

	@Test
	@DisplayName("Should reject login with 401 INVALID_CREDENTIALS when password does not match")
	void shouldRejectLoginWhenPasswordDoesNotMatch() {
		User user = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.passwordHash("hashed-pw")
				.emailVerified(true)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Wrong@123", "hashed-pw")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "Wrong@123")))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
				});
	}

	@Test
	@DisplayName("Should reject login with 403 EMAIL_NOT_VERIFIED when user has not verified email")
	void shouldRejectLoginWhenEmailNotVerified() {
		User user = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.passwordHash("hashed-pw")
				.emailVerified(false)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Strong@123", "hashed-pw")).thenReturn(true);

		assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "Strong@123")))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
				});
	}

}
