package com.m2.orbita_api.controller;

import com.m2.orbita_api.model.dto.request.UserRequest;
import com.m2.orbita_api.model.dto.request.VerificationCodeRequest;
import com.m2.orbita_api.model.dto.response.UserResponse;
import com.m2.orbita_api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Void> save(@RequestBody @Valid UserRequest request) {
        UserResponse response = userService.save(request);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).build();
    }

    @PostMapping("/verification-code")
    public ResponseEntity<Void> verifyEmail(@RequestBody @Valid VerificationCodeRequest request) {
        userService.verifyEmail(request);

        return ResponseEntity.noContent().build();
    }
}
