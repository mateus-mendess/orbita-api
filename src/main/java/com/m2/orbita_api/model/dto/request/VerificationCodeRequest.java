package com.m2.orbita_api.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerificationCodeRequest(
        @NotBlank
        String code,

        @NotNull
        UUID userId
) {}
