package com.m2.orbita_api.infra.exception;

public class NotFoundException extends OrbitaApiException {
    public NotFoundException(String message) {
        super(message);
    }
}
