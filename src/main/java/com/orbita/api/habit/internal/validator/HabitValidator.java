package com.orbita.api.habit.internal.validator;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class HabitValidator {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	public void validateHabit(String title, List<Integer> daysOfWeek) {
		if (title == null || title.trim().isEmpty()) {
			throw new FieldValidationException("title", ErrorCode.REQUIRED);
		}
		if (daysOfWeek == null || daysOfWeek.isEmpty()) {
			throw new FieldValidationException("daysOfWeek", ErrorCode.REQUIRED);
		}
		if (daysOfWeek.size() > 7) {
			throw new FieldValidationException("daysOfWeek", ErrorCode.INVALID_VALUE);
		}

		Set<Integer> uniqueDays = new HashSet<>();
		for (Integer day : daysOfWeek) {
			if (day == null || day < 0 || day > 6 || !uniqueDays.add(day)) {
				throw new FieldValidationException("daysOfWeek", ErrorCode.INVALID_VALUE);
			}
		}
	}

	public void validateCompletionDate(String date) {
		if (date == null || date.isBlank()) {
			throw new FieldValidationException("date", ErrorCode.REQUIRED);
		}

		final LocalDate parsedDate;
		try {
			parsedDate = LocalDate.parse(date, DATE_FORMATTER);
		} catch (DateTimeParseException _) {
			throw new FieldValidationException("date", ErrorCode.INVALID_VALUE);
		}

		LocalDate maxAllowedDate = LocalDate.now(ZoneOffset.UTC).plusDays(1);
		if (parsedDate.isAfter(maxAllowedDate)) {
			throw new FieldValidationException("date", ErrorCode.INVALID_VALUE);
		}
	}

}
