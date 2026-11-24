package com.orbita.api.note.internal.dto;

import com.orbita.api.common.model.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteRequest(
		@NotBlank(message = "REQUIRED")
		@Size(max = 120, message = "INVALID_VALUE")
		String title,

		@NotNull(message = "REQUIRED")
		Category category,

		@Size(max = 100000, message = "INVALID_VALUE")
		String content
) {
}
