package com.m2.orbita_api.controller;

import com.m2.orbita_api.model.dto.request.UserRequest;
import com.m2.orbita_api.model.dto.request.VerificationCodeRequest;
import com.m2.orbita_api.model.dto.response.UserResponse;
import com.m2.orbita_api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Users", description = "Operations related to user registration and email verification.")
@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @Operation(summary = "Register user", description = """
            Registers a new user in the system. The email must be unique
            and not previously registered. A verification code is sent
            to the provided email to complete the registration process.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> save(@RequestBody @Valid UserRequest request) {
        UserResponse response = userService.save(request);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Verify email", description = """
            Confirms a pending user registration by validating the
            verification code sent to the user's email. Completes the
            sign-up process on success.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid verification code"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "410", description = "Verification code expired")
    })
    @PostMapping("/verification-code")
    public ResponseEntity<Void> verifyEmail(@RequestBody @Valid VerificationCodeRequest request) {
        userService.verifyEmail(request);

        return ResponseEntity.noContent().build();
    }
}
