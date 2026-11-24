package com.orbita.api.note.internal.dto;

import com.orbita.api.common.model.Category;

import java.time.Instant;

public record NoteResponse(
		String id,
		String title,
		Category category,
		String content,
		Instant createdAt,
		Instant updatedAt
) {
}
