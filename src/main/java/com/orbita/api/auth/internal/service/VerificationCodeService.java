package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.auth.internal.model.VerificationRecord;
import com.orbita.api.auth.internal.repository.VerificationCodeRepository;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

	private static final int MAX_ATTEMPTS = 5;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final VerificationCodeRepository verificationCodeRepository;

	public String generateAndStore(VerificationPurpose purpose, String identifier, boolean enforceCooldown) {
		if (enforceCooldown) {
			boolean acquired = verificationCodeRepository.acquireCooldown(purpose, identifier);
			if (!acquired) {
				throw new OrbitaApiException(ErrorCode.RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS);
			}
		} else {
			verificationCodeRepository.acquireCooldown(purpose, identifier);
		}

		String rawCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
		verificationCodeRepository.saveCode(purpose, identifier, hashCode(rawCode));
		return rawCode;
	}

	public void enforceCooldownOnly(VerificationPurpose purpose, String identifier) {
		boolean acquired = verificationCodeRepository.acquireCooldown(purpose, identifier);
		if (!acquired) {
			throw new OrbitaApiException(ErrorCode.RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS);
		}
	}

	public void verify(VerificationPurpose purpose, String identifier, String candidateCode, boolean consume) {
		VerificationRecord record = verificationCodeRepository.findCode(purpose, identifier)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.VERIFICATION_CODE_EXPIRED, HttpStatus.GONE));

		if (record.attempts() >= MAX_ATTEMPTS) {
			verificationCodeRepository.deleteCode(purpose, identifier);
			throw new OrbitaApiException(ErrorCode.TOO_MANY_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS);
		}

		String candidateHash = hashCode(candidateCode.trim());
		boolean matches = MessageDigest.isEqual(
				record.codeHash().getBytes(StandardCharsets.UTF_8),
				candidateHash.getBytes(StandardCharsets.UTF_8)
		);

		if (!matches) {
			long attempts = verificationCodeRepository.incrementAttempts(purpose, identifier);
			if (attempts < 0) {
				throw new OrbitaApiException(ErrorCode.VERIFICATION_CODE_EXPIRED, HttpStatus.GONE);
			}
			if (attempts >= MAX_ATTEMPTS) {
				verificationCodeRepository.deleteCode(purpose, identifier);
				throw new OrbitaApiException(ErrorCode.TOO_MANY_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS);
			}
			throw new OrbitaApiException(ErrorCode.INVALID_VERIFICATION_CODE, HttpStatus.BAD_REQUEST);
		}

		if (consume) {
			verificationCodeRepository.deleteCode(purpose, identifier);
		}
	}

	private String hashCode(String rawCode) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

}
