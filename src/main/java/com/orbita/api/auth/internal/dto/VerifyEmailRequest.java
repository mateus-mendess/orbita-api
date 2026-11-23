package com.orbita.api.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
		@NotBlank(message = "REQUIRED")
		String userId,

		@NotBlank(message = "REQUIRED")
		String code
) {
}
