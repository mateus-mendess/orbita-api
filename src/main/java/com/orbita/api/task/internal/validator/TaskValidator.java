package com.orbita.api.task.internal.validator;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;

@Component
public class TaskValidator {

	private static final long MAX_RANGE_DAYS = 366;
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
			.withResolverStyle(ResolverStyle.STRICT);

	public void validateTaskSchedule(String title, String date, String startTime, String endTime) {
		if (title == null || title.trim().isEmpty()) {
			throw new FieldValidationException("title", ErrorCode.REQUIRED);
		}
		if (title.trim().length() > 120) {
			throw new FieldValidationException("title", ErrorCode.INVALID_VALUE);
		}

		parseDate("date", date);
		LocalTime start = parseTime("startTime", startTime);
		LocalTime end = parseTime("endTime", endTime);

		if (!end.isAfter(start)) {
			throw new FieldValidationException("endTime", ErrorCode.INVALID_TIME_RANGE);
		}
	}

	public void validateDateRange(String from, String to) {
		if (from == null || from.isBlank()) {
			throw new FieldValidationException("from", ErrorCode.REQUIRED);
		}
		if (to == null || to.isBlank()) {
			throw new FieldValidationException("to", ErrorCode.REQUIRED);
		}

		LocalDate fromDate = parseDate("from", from);
		LocalDate toDate = parseDate("to", to);

		if (toDate.isBefore(fromDate)) {
			throw new FieldValidationException("to", ErrorCode.INVALID_VALUE);
		}
		if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_RANGE_DAYS) {
			throw new FieldValidationException("to", ErrorCode.INVALID_VALUE);
		}
	}

	private LocalDate parseDate(String field, String value) {
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException | NullPointerException _) {
			throw new FieldValidationException(field, ErrorCode.INVALID_VALUE);
		}
	}

	private LocalTime parseTime(String field, String value) {
		try {
			return LocalTime.parse(value, TIME_FORMATTER);
		} catch (DateTimeParseException | NullPointerException _) {
			throw new FieldValidationException(field, ErrorCode.INVALID_VALUE);
		}
	}

}
