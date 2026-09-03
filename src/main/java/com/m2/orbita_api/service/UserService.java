package com.m2.orbita_api.service;

import com.m2.orbita_api.infra.exception.EmailAlreadyExistsException;
import com.m2.orbita_api.mapper.UserMapper;
import com.m2.orbita_api.model.dto.request.UserRequest;
import com.m2.orbita_api.model.dto.response.UserResponse;
import com.m2.orbita_api.model.entity.User;
import com.m2.orbita_api.model.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserResponse save(UserRequest request) {
        validate(request);

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userMapper.toResponse(userRepository.save(user));
    }

    private void validate(UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw  new EmailAlreadyExistsException("Email already exists");
        }
    }
}
