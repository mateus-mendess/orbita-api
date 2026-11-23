package com.orbita.api.common.exception;

import java.util.List;

public class FieldValidationException extends RuntimeException {

	private final List<GlobalExceptionHandler.FieldErrorDetail> details;

	public FieldValidationException(String field, ErrorCode code) {
		super(ErrorCode.VALIDATION_ERROR.name());
		this.details = List.of(new GlobalExceptionHandler.FieldErrorDetail(field, code.name()));
	}

	public List<GlobalExceptionHandler.FieldErrorDetail> getDetails() {
		return details;
	}

}
