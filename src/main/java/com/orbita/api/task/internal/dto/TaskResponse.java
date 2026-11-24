package com.orbita.api.task.internal.dto;

import com.orbita.api.common.model.Category;
import com.orbita.api.task.internal.model.Urgency;

public record TaskResponse(
		String id,
		String title,
		String description,
		String date,
		String startTime,
		String endTime,
		Urgency urgency,
		Category category,
		boolean completed
) {
}
