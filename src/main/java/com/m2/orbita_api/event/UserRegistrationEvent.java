package com.m2.orbita_api.event;

import java.util.UUID;

public record UserRegistrationEvent(String email, UUID userId) {
}
