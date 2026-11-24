package com.orbita.api.task.internal.dto;

import jakarta.validation.constraints.NotNull;

public record TaskCompletionRequest(
		@NotNull(message = "REQUIRED")
		Boolean completed
) {
}
