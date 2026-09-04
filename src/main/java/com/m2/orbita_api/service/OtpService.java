package com.m2.orbita_api.service;

import com.m2.orbita_api.infra.exception.CodeExpiredException;
import com.m2.orbita_api.infra.exception.InvalidCodeException;
import com.m2.orbita_api.model.dto.request.VerificationCodeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class OtpService {
    private final CacheManager cacheManager;


    @CachePut(value = "code", key = "#userId")
    public String generateCode(UUID userId) {
        SecureRandom secure =  new SecureRandom();

        return String.format("%06d", secure.nextInt(100000));
    }

    public void validate(VerificationCodeRequest request) {
        Cache cache = cacheManager.getCache("code");
        String cachedCode = cache.get(request.userId(),  String.class);

        if (cachedCode == null) {
            throw  new CodeExpiredException("Code expired");
        }
        if (!cachedCode.equals(request.code())) {
            throw new InvalidCodeException("Invalid code");
        }

        cache.evict(request.userId());
    }
}
