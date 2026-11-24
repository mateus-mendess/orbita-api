package com.orbita.api.habit.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HabitRequest(
		@NotBlank(message = "REQUIRED")
		@Size(max = 80, message = "INVALID_VALUE")
		String title,

		@NotBlank(message = "REQUIRED")
		@Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "INVALID_VALUE")
		String icon,

		@NotEmpty(message = "REQUIRED")
		@Size(min = 1, max = 7, message = "INVALID_VALUE")
		List<@NotNull(message = "INVALID_VALUE") Integer> daysOfWeek
) {
}
