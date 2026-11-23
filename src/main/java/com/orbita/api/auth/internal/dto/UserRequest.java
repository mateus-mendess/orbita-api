package com.orbita.api.auth.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserRequest(
		@NotBlank(message = "REQUIRED")
		String name,

		@NotBlank(message = "REQUIRED")
		@Email(message = "INVALID_EMAIL")
		String email,

		@NotBlank(message = "REQUIRED")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
				message = "WEAK_PASSWORD"
		)
		String password,

		@NotBlank(message = "REQUIRED")
		String confirmPassword
) {
}
