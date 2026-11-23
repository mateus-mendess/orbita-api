package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.common.config.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthMailService {

	private final JavaMailSender mailSender;
	private final MailProperties mailProperties;

	public void sendCode(VerificationPurpose purpose, String toEmail, String code) {
		String subject = switch (purpose) {
			case REGISTER -> "Orbita - Verification Code";
			case PASSWORD_RESET -> "Orbita - Password Recovery Code";
		};
		String text = switch (purpose) {
			case REGISTER -> "Your verification code is: " + code;
			case PASSWORD_RESET -> "Your password recovery code is: " + code;
		};

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(mailProperties.from());
		message.setTo(toEmail);
		message.setSubject(subject);
		message.setText(text);
		mailSender.send(message);
	}

}
