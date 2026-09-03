package com.m2.orbita_api.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

public record UserRequest(
        @NotBlank(message = "name required")
        String name,

        @NotBlank(message = "email required")
        @Email(message = "email invalid")
        String email,

        @NotBlank(message = "password required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$",
        message = "The password must contain at least 8 characters, including uppercase, lowercase, numbers, and special characters.")
        String password,

        @NotBlank(message = "confirmPassword required")
        String confirmPassword
) {
    @AssertTrue(message = "passwords must match")
    public boolean isConfirmPasswordValid() {
        return Objects.equals(password, confirmPassword);
    }
}
