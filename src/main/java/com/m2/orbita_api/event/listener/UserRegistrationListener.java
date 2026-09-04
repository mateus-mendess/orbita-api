package com.m2.orbita_api.event.listener;

import com.m2.orbita_api.event.UserRegistrationEvent;
import com.m2.orbita_api.service.EmailService;
import com.m2.orbita_api.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserRegistrationListener {
    private final OtpService otpService;
    private final EmailService emailService;

    @EventListener
    public void handleUserRegistrationEvent(UserRegistrationEvent event) {
        String code = otpService.generateCode();

        String message = "Código de verificação: " + code;
        emailService.sendEmail(event.email(), "Orbita - App", message);
    }
}
