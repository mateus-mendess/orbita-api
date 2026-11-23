package com.orbita.api.auth.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
		@NotBlank(message = "REQUIRED")
		@Email(message = "INVALID_EMAIL")
		String email,

		@NotBlank(message = "REQUIRED")
		String code,

		@NotBlank(message = "REQUIRED")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
				message = "WEAK_PASSWORD"
		)
		String newPassword,

		@NotBlank(message = "REQUIRED")
		String confirmPassword
) {
}
