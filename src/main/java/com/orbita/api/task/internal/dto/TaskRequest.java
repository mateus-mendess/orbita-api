package com.orbita.api.task.internal.dto;

import com.orbita.api.common.model.Category;
import com.orbita.api.task.internal.model.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRequest(
		@NotBlank(message = "REQUIRED")
		@Size(max = 120, message = "INVALID_VALUE")
		String title,

		@Size(max = 2000, message = "INVALID_VALUE")
		String description,

		@NotBlank(message = "REQUIRED")
		String date,

		@NotBlank(message = "REQUIRED")
		String startTime,

		@NotBlank(message = "REQUIRED")
		String endTime,

		@NotNull(message = "REQUIRED")
		Urgency urgency,

		@NotNull(message = "REQUIRED")
		Category category
) {
}
