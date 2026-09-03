package com.m2.orbita_api.model.dto.response;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name
) {}
