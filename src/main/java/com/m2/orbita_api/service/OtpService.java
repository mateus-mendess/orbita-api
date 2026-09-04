package com.m2.orbita_api.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class OtpService {

    public String generateCode() {
        SecureRandom secure =  new SecureRandom();

        return String.format("%06d", secure.nextInt(100000));
    }
}
