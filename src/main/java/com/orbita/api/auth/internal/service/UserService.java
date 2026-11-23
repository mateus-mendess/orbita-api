package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.dto.PasswordResetCheckRequest;
import com.orbita.api.auth.internal.dto.PasswordResetConfirmRequest;
import com.orbita.api.auth.internal.dto.PasswordResetRequest;
import com.orbita.api.auth.internal.dto.RegisterResponse;
import com.orbita.api.auth.internal.dto.ResendVerificationRequest;
import com.orbita.api.auth.internal.dto.TokenResponse;
import com.orbita.api.auth.internal.dto.UserMeResponse;
import com.orbita.api.auth.internal.dto.UserRequest;
import com.orbita.api.auth.internal.dto.VerifyEmailRequest;
import com.orbita.api.auth.internal.mapper.UserMapper;
import com.orbita.api.auth.internal.model.User;
import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.auth.internal.repository.UserRepository;
import com.orbita.api.auth.internal.validator.AuthValidator;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final VerificationCodeService verificationCodeService;
	private final AuthMailService authMailService;
	private final AuthValidator authValidator;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public RegisterResponse register(UserRequest request) {
		authValidator.validatePasswordsMatch(request.password(), request.confirmPassword());

		String trimmedName = request.name().trim();
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		String passwordHash = passwordEncoder.encode(request.password());
		Instant now = Instant.now();

		User user = userRepository.findByEmail(normalizedEmail)
				.map(existing -> {
					if (existing.isEmailVerified()) {
						throw new OrbitaApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT);
					}
					existing.setName(trimmedName);
					existing.setPasswordHash(passwordHash);
					existing.setUpdatedAt(now);
					return existing;
				})
				.orElseGet(() -> User.builder()
						.name(trimmedName)
						.email(normalizedEmail)
						.passwordHash(passwordHash)
						.emailVerified(false)
						.createdAt(now)
						.updatedAt(now)
						.build());

		User savedUser;
		try {
			savedUser = userRepository.save(user);
		} catch (DuplicateKeyException _) {
			throw new OrbitaApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT);
		}

		String code = verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, savedUser.getId(), false);
		authMailService.sendCode(VerificationPurpose.REGISTER, normalizedEmail, code);

		return userMapper.toRegisterResponse(savedUser);
	}

	public TokenResponse verifyEmail(VerifyEmailRequest request) {
		String userId = request.userId().trim();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

		verificationCodeService.verify(VerificationPurpose.REGISTER, user.getId(), request.code(), true);

		if (!user.isEmailVerified()) {
			user.setEmailVerified(true);
			user.setUpdatedAt(Instant.now());
			userRepository.save(user);
		}

		String accessToken = jwtService.generateAccessToken(user.getId());
		return new TokenResponse(accessToken);
	}

	public void resendVerification(ResendVerificationRequest request) {
		String userId = request.userId().trim();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

		String code = verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, user.getId(), true);
		authMailService.sendCode(VerificationPurpose.REGISTER, user.getEmail(), code);
	}

	public void requestPasswordReset(PasswordResetRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		userRepository.findByEmail(normalizedEmail).ifPresentOrElse(user -> {
			if (!user.isEmailVerified()) {
				verificationCodeService.enforceCooldownOnly(VerificationPurpose.PASSWORD_RESET, normalizedEmail);
				return;
			}
			String code = verificationCodeService.generateAndStore(
					VerificationPurpose.PASSWORD_RESET,
					normalizedEmail,
					true
			);
			authMailService.sendCode(VerificationPurpose.PASSWORD_RESET, normalizedEmail, code);
		}, () -> verificationCodeService.enforceCooldownOnly(VerificationPurpose.PASSWORD_RESET, normalizedEmail));
	}

	public void checkPasswordResetCode(PasswordResetCheckRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		verificationCodeService.verify(VerificationPurpose.PASSWORD_RESET, normalizedEmail, request.code(), false);
	}

	public void confirmPasswordReset(PasswordResetConfirmRequest request) {
		authValidator.validatePasswordsMatch(request.newPassword(), request.confirmPassword());

		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		verificationCodeService.verify(VerificationPurpose.PASSWORD_RESET, normalizedEmail, request.code(), true);

		User user = userRepository.findByEmail(normalizedEmail)
				.filter(User::isEmailVerified)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.VERIFICATION_CODE_EXPIRED, HttpStatus.GONE));

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);
	}

	public UserMeResponse getMe(String userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED));
		return userMapper.toUserMeResponse(user);
	}

}
