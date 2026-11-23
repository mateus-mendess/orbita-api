package com.orbita.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OrbitaApiException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus status;

	public OrbitaApiException(ErrorCode errorCode, HttpStatus status) {
		super(errorCode.name());
		this.errorCode = errorCode;
		this.status = status;
	}

}
