package com.orbita.api.habit.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HabitCompletionRequest(
		@NotBlank(message = "REQUIRED")
		String date,

		@NotNull(message = "REQUIRED")
		Boolean completed
) {
}
