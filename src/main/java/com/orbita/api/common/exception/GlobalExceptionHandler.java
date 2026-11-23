package com.orbita.api.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorResponse(String code, List<FieldErrorDetail> details) {
		public static ErrorResponse of(ErrorCode errorCode) {
			return new ErrorResponse(errorCode.name(), null);
		}

		public static ErrorResponse ofValidation(List<FieldErrorDetail> details) {
			return new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), details);
		}
	}

	public record FieldErrorDetail(String field, String code) {
	}

	@ExceptionHandler(OrbitaApiException.class)
	public ResponseEntity<ErrorResponse> handleAppException(OrbitaApiException ex) {
		return ResponseEntity
				.status(ex.getStatus())
				.body(ErrorResponse.of(ex.getErrorCode()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
		List<FieldErrorDetail> details = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(err -> new FieldErrorDetail(err.getField(), resolveValidationCode(err.getDefaultMessage())))
				.toList();

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.ofValidation(details));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
		List<FieldErrorDetail> details = List.of(
				new FieldErrorDetail(ex.getParameterName(), ErrorCode.REQUIRED.name())
		);
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.ofValidation(details));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		List<FieldErrorDetail> details = List.of(
				new FieldErrorDetail(ex.getName(), ErrorCode.INVALID_VALUE.name())
		);
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.ofValidation(details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

	private String resolveValidationCode(String message) {
		if (message == null || message.isBlank()) {
			return ErrorCode.INVALID_VALUE.name();
		}
		try {
			return ErrorCode.valueOf(message).name();
		} catch (IllegalArgumentException _) {
			return ErrorCode.INVALID_VALUE.name();
		}
	}

}
