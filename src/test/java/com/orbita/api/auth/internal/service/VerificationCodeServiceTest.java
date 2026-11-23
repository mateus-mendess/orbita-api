package com.orbita.api.auth.internal.service;

import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.auth.internal.model.VerificationRecord;
import com.orbita.api.auth.internal.repository.VerificationCodeRepository;
import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

	@Mock
	private VerificationCodeRepository verificationCodeRepository;

	@InjectMocks
	private VerificationCodeService verificationCodeService;

	@Test
	@DisplayName("Should generate 6-digit code and store its SHA-256 hash")
	void shouldGenerateAndStoreCode() {
		when(verificationCodeRepository.acquireCooldown(VerificationPurpose.REGISTER, "user-1")).thenReturn(true);

		String rawCode = verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, "user-1", true);

		assertThat(rawCode).matches("^\\d{6}$");
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(verificationCodeRepository).saveCode(eq(VerificationPurpose.REGISTER), eq("user-1"), hashCaptor.capture());
		assertThat(hashCaptor.getValue()).hasSize(64).doesNotContain(rawCode);
	}

	@Test
	@DisplayName("Should throw 429 RESEND_COOLDOWN when cooldown is enforced and active")
	void shouldThrowWhenCooldownIsActive() {
		when(verificationCodeRepository.acquireCooldown(VerificationPurpose.REGISTER, "user-1")).thenReturn(false);

		assertThatThrownBy(() -> verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, "user-1", true))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.RESEND_COOLDOWN);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				});

		verify(verificationCodeRepository, never()).saveCode(eq(VerificationPurpose.REGISTER), anyString(), anyString());
	}

	@Test
	@DisplayName("Should throw 410 VERIFICATION_CODE_EXPIRED when record does not exist")
	void shouldThrowWhenCodeExpired() {
		when(verificationCodeRepository.findCode(VerificationPurpose.REGISTER, "user-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> verificationCodeService.verify(VerificationPurpose.REGISTER, "user-1", "123456", true))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.GONE);
				});
	}

	@Test
	@DisplayName("Should increment attempts and throw 400 INVALID_VERIFICATION_CODE when code is wrong and attempts < 5")
	void shouldIncrementAttemptsOnWrongCode() {
		VerificationRecord record = new VerificationRecord("00".repeat(32), 2);
		when(verificationCodeRepository.findCode(VerificationPurpose.REGISTER, "user-1")).thenReturn(Optional.of(record));
		when(verificationCodeRepository.incrementAttempts(VerificationPurpose.REGISTER, "user-1")).thenReturn(3L);

		assertThatThrownBy(() -> verificationCodeService.verify(VerificationPurpose.REGISTER, "user-1", "123456", true))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
				});

		verify(verificationCodeRepository, never()).deleteCode(VerificationPurpose.REGISTER, "user-1");
	}

	@Test
	@DisplayName("Should throw 410 VERIFICATION_CODE_EXPIRED when key expires before incrementAttempts executes")
	void shouldThrowExpiredWhenKeyExpiresBeforeIncrementAttempts() {
		VerificationRecord record = new VerificationRecord("00".repeat(32), 1);
		when(verificationCodeRepository.findCode(VerificationPurpose.REGISTER, "user-1")).thenReturn(Optional.of(record));
		when(verificationCodeRepository.incrementAttempts(VerificationPurpose.REGISTER, "user-1")).thenReturn(-1L);

		assertThatThrownBy(() -> verificationCodeService.verify(VerificationPurpose.REGISTER, "user-1", "123456", true))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.GONE);
				});

		verify(verificationCodeRepository, never()).deleteCode(VerificationPurpose.REGISTER, "user-1");
	}

	@Test
	@DisplayName("Should delete code and throw 429 TOO_MANY_ATTEMPTS when 5th attempt fails")
	void shouldDeleteAndThrowOnFifthFailedAttempt() {
		VerificationRecord record = new VerificationRecord("00".repeat(32), 4);
		when(verificationCodeRepository.findCode(VerificationPurpose.REGISTER, "user-1")).thenReturn(Optional.of(record));
		when(verificationCodeRepository.incrementAttempts(VerificationPurpose.REGISTER, "user-1")).thenReturn(5L);

		assertThatThrownBy(() -> verificationCodeService.verify(VerificationPurpose.REGISTER, "user-1", "123456", true))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				});

		verify(verificationCodeRepository).deleteCode(VerificationPurpose.REGISTER, "user-1");
	}

	@Test
	@DisplayName("Should verify and consume code when consume is true")
	void shouldVerifyAndConsumeCode() {
		when(verificationCodeRepository.acquireCooldown(VerificationPurpose.REGISTER, "user-1")).thenReturn(true);
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		String rawCode = verificationCodeService.generateAndStore(VerificationPurpose.REGISTER, "user-1", false);
		verify(verificationCodeRepository).saveCode(eq(VerificationPurpose.REGISTER), eq("user-1"), hashCaptor.capture());

		VerificationRecord record = new VerificationRecord(hashCaptor.getValue(), 0);
		when(verificationCodeRepository.findCode(VerificationPurpose.REGISTER, "user-1")).thenReturn(Optional.of(record));

		verificationCodeService.verify(VerificationPurpose.REGISTER, "user-1", rawCode, true);

		verify(verificationCodeRepository).deleteCode(VerificationPurpose.REGISTER, "user-1");
	}

	@Test
	@DisplayName("Should verify without deleting code when consume is false")
	void shouldVerifyWithoutConsumingCode() {
		when(verificationCodeRepository.acquireCooldown(VerificationPurpose.PASSWORD_RESET, "user@test.com")).thenReturn(true);
		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		String rawCode = verificationCodeService.generateAndStore(VerificationPurpose.PASSWORD_RESET, "user@test.com", false);
		verify(verificationCodeRepository).saveCode(eq(VerificationPurpose.PASSWORD_RESET), eq("user@test.com"), hashCaptor.capture());

		VerificationRecord record = new VerificationRecord(hashCaptor.getValue(), 0);
		when(verificationCodeRepository.findCode(VerificationPurpose.PASSWORD_RESET, "user@test.com")).thenReturn(Optional.of(record));

		verificationCodeService.verify(VerificationPurpose.PASSWORD_RESET, "user@test.com", rawCode, false);

		verify(verificationCodeRepository, never()).deleteCode(VerificationPurpose.PASSWORD_RESET, "user@test.com");
	}

}
