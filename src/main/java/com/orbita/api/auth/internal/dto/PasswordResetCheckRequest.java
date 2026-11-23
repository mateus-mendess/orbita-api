package com.orbita.api.auth.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetCheckRequest(
		@NotBlank(message = "REQUIRED")
		@Email(message = "INVALID_EMAIL")
		String email,

		@NotBlank(message = "REQUIRED")
		String code
) {
}
