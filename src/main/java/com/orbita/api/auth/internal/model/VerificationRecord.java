package com.orbita.api.auth.internal.model;

public record VerificationRecord(String codeHash, int attempts) {
}
