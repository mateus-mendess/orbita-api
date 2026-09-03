package com.m2.orbita_api.infra.exception;

public class EmailAlreadyExistsException extends OrbitaApiException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
