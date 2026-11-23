package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.dto.PasswordResetConfirmRequest;
import com.orbita.api.auth.internal.dto.PasswordResetRequest;
import com.orbita.api.auth.internal.dto.RegisterResponse;
import com.orbita.api.auth.internal.dto.TokenResponse;
import com.orbita.api.auth.internal.dto.UserRequest;
import com.orbita.api.auth.internal.dto.VerifyEmailRequest;
import com.orbita.api.auth.internal.mapper.UserMapper;
import com.orbita.api.auth.internal.model.User;
import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.auth.internal.repository.UserRepository;
import com.orbita.api.auth.internal.validator.AuthValidator;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private VerificationCodeService verificationCodeService;

	@Mock
	private AuthMailService authMailService;

	@Mock
	private UserMapper userMapper;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(
				userRepository,
				verificationCodeService,
				authMailService,
				new AuthValidator(),
				userMapper,
				passwordEncoder,
				jwtService
		);
	}

	@Test
	@DisplayName("Should register user with trimmed name, lowercase email, and send verification code")
	void shouldRegisterNewUser() {
		UserRequest request = new UserRequest("  Alice  ", "  Alice@Example.com ", "Strong@123", "Strong@123");
		User savedUser = User.builder()
				.id("user-123")
				.name("Alice")
				.email("alice@example.com")
				.passwordHash("hashed-pw")
				.emailVerified(false)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("Strong@123")).thenReturn("hashed-pw");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		when(verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, "user-123", false)).thenReturn("654321");
		when(userMapper.toRegisterResponse(savedUser)).thenReturn(new RegisterResponse("user-123"));

		RegisterResponse response = userService.register(request);

		assertThat(response.id()).isEqualTo("user-123");
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getName()).isEqualTo("Alice");
		assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
		assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
		assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
		assertThat(userCaptor.getValue().getCreatedAt()).isNotNull();
		assertThat(userCaptor.getValue().getUpdatedAt()).isNotNull();
		verify(authMailService).sendCode(VerificationPurpose.REGISTER, "alice@example.com", "654321");
	}

	@Test
	@DisplayName("Should update existing unverified user on re-registration and resend verification code")
	void shouldUpdateExistingUnverifiedUserOnRegister() {
		UserRequest request = new UserRequest("Alice Updated", "alice@example.com", "NewStrong@123", "NewStrong@123");
		User existingUnverified = User.builder()
				.id("user-123")
				.name("Alice Old")
				.email("alice@example.com")
				.passwordHash("old-hash")
				.emailVerified(false)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUnverified));
		when(passwordEncoder.encode("NewStrong@123")).thenReturn("new-hash");
		when(userRepository.save(existingUnverified)).thenReturn(existingUnverified);
		when(verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, "user-123", false)).thenReturn("112233");
		when(userMapper.toRegisterResponse(existingUnverified)).thenReturn(new RegisterResponse("user-123"));

		RegisterResponse response = userService.register(request);

		assertThat(response.id()).isEqualTo("user-123");
		assertThat(existingUnverified.getName()).isEqualTo("Alice Updated");
		assertThat(existingUnverified.getPasswordHash()).isEqualTo("new-hash");
		verify(userRepository).save(existingUnverified);
		verify(authMailService).sendCode(VerificationPurpose.REGISTER, "alice@example.com", "112233");
	}

	@Test
	@DisplayName("Should reject registration with 409 EMAIL_ALREADY_REGISTERED when verified user already exists")
	void shouldRejectRegistrationWhenVerifiedUserExists() {
		UserRequest request = new UserRequest("Alice", "alice@example.com", "Strong@123", "Strong@123");
		User verifiedUser = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.emailVerified(true)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));

		assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				});

		verify(userRepository, never()).save(any(User.class));
		verifyNoInteractions(verificationCodeService, authMailService);
	}

	@Test
	@DisplayName("Should translate DuplicateKeyException into 409 EMAIL_ALREADY_REGISTERED")
	void shouldTranslateDuplicateKeyExceptionToConflict() {
		UserRequest request = new UserRequest("Alice", "alice@example.com", "Strong@123", "Strong@123");

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("Strong@123")).thenReturn("hashed-pw");
		when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

		assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				});

		verifyNoInteractions(verificationCodeService, authMailService);
	}

	@Test
	@DisplayName("Should reject registration when password and confirmPassword do not match")
	void shouldRejectRegistrationWhenPasswordsDoNotMatch() {
		UserRequest request = new UserRequest("Alice", "alice@example.com", "Strong@123", "Different@123");

		assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("confirmPassword");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.PASSWORDS_DO_NOT_MATCH.name());
				});

		verifyNoInteractions(userRepository, verificationCodeService, authMailService);
	}

	@Test
	@DisplayName("Should verify email, mark user verified, and return JWT access token")
	void shouldVerifyEmailAndReturnToken() {
		User user = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.emailVerified(false)
				.build();

		when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
		when(jwtService.generateAccessToken("user-123")).thenReturn("jwt-token-xyz");

		TokenResponse response = userService.verifyEmail(new VerifyEmailRequest("user-123", "123456"));

		assertThat(response.accessToken()).isEqualTo("jwt-token-xyz");
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getUpdatedAt()).isNotNull();
		verify(verificationCodeService).verify(VerificationPurpose.REGISTER, "user-123", "123456", true);
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("Should not send password reset email when account is unverified")
	void shouldNotSendPasswordResetForUnverifiedAccount() {
		User unverifiedUser = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.emailVerified(false)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(unverifiedUser));

		userService.requestPasswordReset(new PasswordResetRequest("alice@example.com"));

		verify(verificationCodeService).enforceCooldownOnly(VerificationPurpose.PASSWORD_RESET, "alice@example.com");
		verify(verificationCodeService, never()).generateAndStore(eq(VerificationPurpose.PASSWORD_RESET), anyString(), anyBoolean());
		verifyNoInteractions(authMailService);
	}

	@Test
	@DisplayName("Should confirm password reset and update user password hash")
	void shouldConfirmPasswordReset() {
		User verifiedUser = User.builder()
				.id("user-123")
				.email("alice@example.com")
				.emailVerified(true)
				.build();

		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));
		when(passwordEncoder.encode("NewStrong@123")).thenReturn("new-hashed-pw");

		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
				"alice@example.com",
				"123456",
				"NewStrong@123",
				"NewStrong@123"
		);

		userService.confirmPasswordReset(request);

		assertThat(verifiedUser.getPasswordHash()).isEqualTo("new-hashed-pw");
		assertThat(verifiedUser.getUpdatedAt()).isNotNull();
		verify(verificationCodeService).verify(VerificationPurpose.PASSWORD_RESET, "alice@example.com", "123456", true);
		verify(userRepository).save(verifiedUser);
	}

	@Test
	@DisplayName("Should reject password reset confirmation when newPassword and confirmPassword do not match")
	void shouldRejectPasswordResetWhenPasswordsDoNotMatch() {
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
				"alice@example.com",
				"123456",
				"NewStrong@123",
				"Different@123"
		);

		assertThatThrownBy(() -> userService.confirmPasswordReset(request))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("confirmPassword");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.PASSWORDS_DO_NOT_MATCH.name());
				});

		verifyNoInteractions(verificationCodeService, userRepository);
	}

}
