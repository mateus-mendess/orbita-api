package com.orbita.api.auth.internal.controller;

import com.orbita.api.auth.internal.dto.PasswordResetCheckRequest;
import com.orbita.api.auth.internal.dto.PasswordResetConfirmRequest;
import com.orbita.api.auth.internal.dto.PasswordResetRequest;
import com.orbita.api.auth.internal.dto.RegisterResponse;
import com.orbita.api.auth.internal.dto.ResendVerificationRequest;
import com.orbita.api.auth.internal.dto.TokenResponse;
import com.orbita.api.auth.internal.dto.UserMeResponse;
import com.orbita.api.auth.internal.dto.UserRequest;
import com.orbita.api.auth.internal.dto.VerifyEmailRequest;
import com.orbita.api.auth.internal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "User registration, e-mail verification, password recovery, and account profile")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "Register a new user account")
	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody UserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
	}

	@Operation(summary = "Verify user e-mail with 6-digit code and return JWT access token")
	@PostMapping("/verify-email")
	public ResponseEntity<TokenResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return ResponseEntity.ok(userService.verifyEmail(request));
	}

	@Operation(summary = "Resend registration e-mail verification code")
	@PostMapping("/resend-verification")
	public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		userService.resendVerification(request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Request a password recovery code")
	@PostMapping("/password-reset/request")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		userService.requestPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Check a password recovery code without consuming it")
	@PostMapping("/password-reset/check")
	public ResponseEntity<Void> checkPasswordResetCode(@Valid @RequestBody PasswordResetCheckRequest request) {
		userService.checkPasswordResetCode(request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Confirm password recovery and update password")
	@PostMapping("/password-reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		userService.confirmPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Get current authenticated user profile")
	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(userService.getMe(jwt.getSubject()));
	}

}
