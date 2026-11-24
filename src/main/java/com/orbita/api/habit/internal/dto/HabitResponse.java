package com.orbita.api.habit.internal.dto;

import java.time.Instant;
import java.util.List;

public record HabitResponse(
		String id,
		String title,
		String icon,
		int frequencyDaysPerWeek,
		List<Integer> daysOfWeek,
		List<String> completedDates,
		Instant createdAt
) {
}
