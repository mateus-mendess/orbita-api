package com.orbita.api.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
		@NotBlank(message = "REQUIRED")
		String userId
) {
}
