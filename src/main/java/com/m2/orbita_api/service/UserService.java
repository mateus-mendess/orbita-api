package com.m2.orbita_api.service;

import com.m2.orbita_api.event.UserRegistrationEvent;
import com.m2.orbita_api.infra.exception.EmailAlreadyExistsException;
import com.m2.orbita_api.infra.exception.NotFoundException;
import com.m2.orbita_api.mapper.UserMapper;
import com.m2.orbita_api.model.dto.request.UserRequest;
import com.m2.orbita_api.model.dto.request.VerificationCodeRequest;
import com.m2.orbita_api.model.dto.response.UserResponse;
import com.m2.orbita_api.model.entity.User;
import com.m2.orbita_api.model.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher publisher;
    private final OtpService otpService;

    public UserResponse save(UserRequest request) {
        validate(request);

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        UserResponse response = userMapper.toResponse(userRepository.save(user));

        publisher.publishEvent(new UserRegistrationEvent(user.getEmail(), user.getId()));

        return response;
    }

    @Transactional
    public void verifyEmail(VerificationCodeRequest request) {
        otpService.validate(request);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setEnabled(true);
    }

    private void validate(UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw  new EmailAlreadyExistsException("Email already exists");
        }
    }
}
