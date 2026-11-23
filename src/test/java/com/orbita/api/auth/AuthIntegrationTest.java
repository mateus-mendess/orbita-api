package com.orbita.api.auth;

import com.orbita.api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

	private static final Pattern CODE_PATTERN = Pattern.compile("(\\d{6})");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void resetMocks() {
		clearInvocations(javaMailSender);
	}

	@Test
	@DisplayName("Should complete full registration, email verification (returning 200 accessToken), login, and /me flow")
	void shouldCompleteRegistrationVerificationLoginAndMeFlow() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@orbita.app";

		// 1. Register
		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "  Maria Silva  ",
									"email": "%s",
									"password": "Password@123",
									"confirmPassword": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andReturn();

		String userId = extractJsonValue(registerResult.getResponse().getContentAsString(), "id");
		String code = extractLastSentCode();

		// 2. Login before verification should fail with 403 EMAIL_NOT_VERIFIED
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"password": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		// 3. Verify email -> 200 OK with accessToken
		MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s",
									"code": "%s"
								}
								""".formatted(userId, code)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String verifyAccessToken = extractJsonValue(verifyResult.getResponse().getContentAsString(), "accessToken");

		// 4. Call /api/v1/auth/me using token from verify-email
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + verifyAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId))
				.andExpect(jsonPath("$.name").value("Maria Silva"))
				.andExpect(jsonPath("$.email").value(email));

		// 5. Login with verified account -> 200 OK with accessToken
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"password": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	@DisplayName("Should replace unverified user on re-register and return 409 EMAIL_ALREADY_REGISTERED once verified")
	void shouldReplaceUnverifiedAndRejectVerifiedDuplicateEmail() throws Exception {
		String email = "dup-" + UUID.randomUUID() + "@orbita.app";

		MvcResult firstRegister = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "First Name",
									"email": "%s",
									"password": "Password@123",
									"confirmPassword": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		String firstId = extractJsonValue(firstRegister.getResponse().getContentAsString(), "id");

		// Re-registering unverified email updates name & password and keeps same userId
		MvcResult secondRegister = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Updated Name",
									"email": "%s",
									"password": "NewPassword@123",
									"confirmPassword": "NewPassword@123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		String secondId = extractJsonValue(secondRegister.getResponse().getContentAsString(), "id");
		assertThat(secondId).isEqualTo(firstId);
		String latestCode = extractLastSentCode();

		// Verify email
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s",
									"code": "%s"
								}
								""".formatted(secondId, latestCode)))
				.andExpect(status().isOk());

		// Re-registering verified email returns 409 EMAIL_ALREADY_REGISTERED
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Third Name",
									"email": "%s",
									"password": "Password@123",
									"confirmPassword": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	@DisplayName("Should enforce verification code rules: 400 wrong code, 429 max 5 attempts, and 429 resend cooldown")
	void shouldEnforceVerificationCodeAttemptsAndCooldown() throws Exception {
		String email = "attempts-" + UUID.randomUUID() + "@orbita.app";

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Test User",
									"email": "%s",
									"password": "Password@123",
									"confirmPassword": "Password@123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		String userId = extractJsonValue(registerResult.getResponse().getContentAsString(), "id");

		// Immediate resend should fail with 429 RESEND_COOLDOWN
		mockMvc.perform(post("/api/v1/auth/resend-verification")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s"
								}
								""".formatted(userId)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RESEND_COOLDOWN"));

		// First 4 wrong attempts -> 400 INVALID_VERIFICATION_CODE
		for (int i = 1; i <= 4; i++) {
			mockMvc.perform(post("/api/v1/auth/verify-email")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
										"userId": "%s",
										"code": "000000"
									}
									""".formatted(userId)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
		}

		// 5th wrong attempt -> 429 TOO_MANY_ATTEMPTS and consumes code
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s",
									"code": "000000"
								}
								""".formatted(userId)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

		// Subsequent attempt after deletion -> 410 VERIFICATION_CODE_EXPIRED
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s",
									"code": "000000"
								}
								""".formatted(userId)))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
	}

	@Test
	@DisplayName("Should complete password reset flow (request -> check -> confirm -> login with new password)")
	void shouldCompletePasswordResetFlow() throws Exception {
		String email = "reset-" + UUID.randomUUID() + "@orbita.app";

		// Register and verify user first
		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Reset User",
									"email": "%s",
									"password": "OldPassword@123",
									"confirmPassword": "OldPassword@123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		String userId = extractJsonValue(registerResult.getResponse().getContentAsString(), "id");
		String registerCode = extractLastSentCode();

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"userId": "%s",
									"code": "%s"
								}
								""".formatted(userId, registerCode)))
				.andExpect(status().isOk());

		// Request password reset
		mockMvc.perform(post("/api/v1/auth/password-reset/request")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s"
								}
								""".formatted(email)))
				.andExpect(status().isNoContent());

		String resetCode = extractLastSentCode();

		// Check code (does not consume it)
		mockMvc.perform(post("/api/v1/auth/password-reset/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"code": "%s"
								}
								""".formatted(email, resetCode)))
				.andExpect(status().isNoContent());

		// Confirm password reset using the same code
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"code": "%s",
									"newPassword": "NewPassword@456",
									"confirmPassword": "NewPassword@456"
								}
								""".formatted(email, resetCode)))
				.andExpect(status().isNoContent());

		// Replaying the same reset code should fail with 410 VERIFICATION_CODE_EXPIRED
		mockMvc.perform(post("/api/v1/auth/password-reset/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"code": "%s"
								}
								""".formatted(email, resetCode)))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));

		// Login with old password fails
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"password": "OldPassword@123"
								}
								""".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		// Login with new password succeeds
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "%s",
									"password": "NewPassword@456"
								}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	@DisplayName("Should return 400 VALIDATION_ERROR with details on invalid input or mismatched passwords, and 401 UNAUTHORIZED on unauthenticated /me")
	void shouldValidateInputsAndProtectAuthenticatedRoutes() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Alice",
									"email": "alice@orbita.app",
									"password": "WeakPassword",
									"confirmPassword": "WeakPassword"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("password"))
				.andExpect(jsonPath("$.details[0].code").value("WEAK_PASSWORD"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"name": "Alice",
									"email": "alice@orbita.app",
									"password": "StrongPassword@123",
									"confirmPassword": "DifferentPassword@123"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("confirmPassword"))
				.andExpect(jsonPath("$.details[0].code").value("PASSWORDS_DO_NOT_MATCH"));

		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private String extractLastSentCode() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(javaMailSender, atLeastOnce()).send(captor.capture());
		SimpleMailMessage lastMessage = captor.getAllValues().getLast();
		Matcher matcher = CODE_PATTERN.matcher(lastMessage.getText());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private String extractJsonValue(String json, String key) {
		Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
		Matcher matcher = pattern.matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

}
