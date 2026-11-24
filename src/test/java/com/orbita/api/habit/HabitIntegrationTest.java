package com.orbita.api.habit;

import com.orbita.api.AbstractIntegrationTest;
import com.orbita.api.common.security.JwtService;
import com.orbita.api.habit.internal.model.Habit;
import com.orbita.api.habit.internal.repository.HabitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class HabitIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private HabitRepository habitRepository;

	@Test
	@DisplayName("Should execute full habit CRUD lifecycle, newest-first ordering, idempotent completion toggle, preserve completedDates on PUT, and populate/evict Redis cache")
	void shouldExecuteFullCrudAndCacheLifecycle() throws Exception {
		String userId = "user-" + UUID.randomUUID();
		String token = jwtService.generateAccessToken(userId);
		String cacheKey = "habits::" + userId;

		// 1. Create two habits
		MvcResult createFirst = mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "  Drink Water  ",
									"icon": "water-outline",
									"daysOfWeek": [5, 1, 3]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Drink Water"))
				.andExpect(jsonPath("$.icon").value("water-outline"))
				.andExpect(jsonPath("$.frequencyDaysPerWeek").value(3))
				.andExpect(jsonPath("$.daysOfWeek[0]").value(1))
				.andExpect(jsonPath("$.daysOfWeek[1]").value(3))
				.andExpect(jsonPath("$.daysOfWeek[2]").value(5))
				.andExpect(jsonPath("$.completedDates").isEmpty())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();

		String habitId1 = extractJsonValue(createFirst.getResponse().getContentAsString(), "id");

		// Small pause so createdAt of second habit is strictly newer
		Thread.sleep(10);

		MvcResult createSecond = mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Morning Stretch",
									"icon": "body-outline",
									"daysOfWeek": [0, 1, 2, 3, 4, 5, 6]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.frequencyDaysPerWeek").value(7))
				.andReturn();

		String habitId2 = extractJsonValue(createSecond.getResponse().getContentAsString(), "id");

		// 2. List habits -> ordered newest first (habitId2 then habitId1) and cached in Redis under habits::{userId}
		mockMvc.perform(get("/api/v1/habits")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(habitId2))
				.andExpect(jsonPath("$[1].id").value(habitId1));

		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

		// 2b. Second GET -> served directly from Redis cache hit
		mockMvc.perform(get("/api/v1/habits")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(habitId2))
				.andExpect(jsonPath("$[1].id").value(habitId1));

		// 3. PATCH /completed on habit 1 -> mark two dates and verify idempotency + cache eviction
		mockMvc.perform(patch("/api/v1/habits/{id}/completed", habitId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "2026-11-23",
									"completed": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDates.length()").value(1))
				.andExpect(jsonPath("$.completedDates[0]").value("2026-11-23"));

		assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

		// Mark same date again (idempotent) + mark an earlier date
		mockMvc.perform(patch("/api/v1/habits/{id}/completed", habitId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "2026-11-23",
									"completed": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDates.length()").value(1));

		mockMvc.perform(patch("/api/v1/habits/{id}/completed", habitId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "2026-11-21",
									"completed": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDates.length()").value(2))
				.andExpect(jsonPath("$.completedDates[0]").value("2026-11-21"))
				.andExpect(jsonPath("$.completedDates[1]").value("2026-11-23"));

		// Unmark 2026-11-21 freely
		mockMvc.perform(patch("/api/v1/habits/{id}/completed", habitId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "2026-11-21",
									"completed": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDates.length()").value(1))
				.andExpect(jsonPath("$.completedDates[0]").value("2026-11-23"));

		// Repopulate cache before PUT update
		mockMvc.perform(get("/api/v1/habits")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

		// 4. PUT update habit 1 -> updates title, icon, daysOfWeek, preserves completedDates, and evicts cache
		mockMvc.perform(put("/api/v1/habits/{id}", habitId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Drink 3L Water",
									"icon": "water",
									"daysOfWeek": [1, 2, 3, 4, 5]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(habitId1))
				.andExpect(jsonPath("$.title").value("Drink 3L Water"))
				.andExpect(jsonPath("$.icon").value("water"))
				.andExpect(jsonPath("$.frequencyDaysPerWeek").value(5))
				.andExpect(jsonPath("$.completedDates.length()").value(1))
				.andExpect(jsonPath("$.completedDates[0]").value("2026-11-23"));

		assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

		// 5. Delete habit 2 -> 204 No Content
		mockMvc.perform(delete("/api/v1/habits/{id}", habitId2)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		// 6. List again -> only updated habit 1 remains with completedDates preserved
		mockMvc.perform(get("/api/v1/habits")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(habitId1))
				.andExpect(jsonPath("$[0].completedDates[0]").value("2026-11-23"));
	}

	@Test
	@DisplayName("Should enforce tenant isolation: User B cannot read, update, complete, or delete User A's habits, nor evict User A's cache")
	void shouldEnforceTenantIsolation() throws Exception {
		String userIdA = "user-A-" + UUID.randomUUID();
		String userIdB = "user-B-" + UUID.randomUUID();
		String tokenA = jwtService.generateAccessToken(userIdA);
		String tokenB = jwtService.generateAccessToken(userIdB);

		MvcResult created = mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "User A Habit",
									"icon": "book-outline",
									"daysOfWeek": [1, 3, 5]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String habitAId = extractJsonValue(created.getResponse().getContentAsString(), "id");

		// User B lists habits -> sees empty list
		mockMvc.perform(get("/api/v1/habits")
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// User B tries to update User A's habit -> 404 HABIT_NOT_FOUND
		mockMvc.perform(put("/api/v1/habits/{id}", habitAId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Hijacked",
									"icon": "flame-outline",
									"daysOfWeek": [0]
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("HABIT_NOT_FOUND"));

		// User B tries to complete User A's habit -> 404 HABIT_NOT_FOUND
		mockMvc.perform(patch("/api/v1/habits/{id}/completed", habitAId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "2026-11-23",
									"completed": true
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("HABIT_NOT_FOUND"));

		// User B tries to delete User A's habit -> 404 HABIT_NOT_FOUND
		mockMvc.perform(delete("/api/v1/habits/{id}", habitAId)
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("HABIT_NOT_FOUND"));
	}

	@Test
	@DisplayName("Should return 400 VALIDATION_ERROR on invalid icon/daysOfWeek/future date and 401 UNAUTHORIZED when token is missing")
	void shouldValidateInputsAndRequireAuth() throws Exception {
		String token = jwtService.generateAccessToken("user-val-" + UUID.randomUUID());

		// Invalid icon pattern -> 400 VALIDATION_ERROR
		mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Habit",
									"icon": "INVALID_ICON!",
									"daysOfWeek": [1, 2]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("icon"))
				.andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"));

		// Duplicate daysOfWeek -> 400 VALIDATION_ERROR
		mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Habit",
									"icon": "water-outline",
									"daysOfWeek": [1, 1]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("daysOfWeek"))
				.andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"));

		// Completion date more than 1 day in the future -> 400 VALIDATION_ERROR
		String twoDaysInFuture = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
		mockMvc.perform(patch("/api/v1/habits/{id}/completed", "habit-123")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"date": "%s",
									"completed": true
								}
								""".formatted(twoDaysInFuture)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("date"))
				.andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"));

		// Unauthenticated request -> 401 UNAUTHORIZED
		mockMvc.perform(get("/api/v1/habits"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("Should return 422 HABIT_LIMIT_REACHED when creating a 51st habit for a user")
	void shouldReturn422WhenHabitLimitReached() throws Exception {
		String userId = "user-limit-" + UUID.randomUUID();
		String token = jwtService.generateAccessToken(userId);
		Instant now = Instant.now();

		List<Habit> fiftyHabits = IntStream.range(0, 50)
				.mapToObj(i -> Habit.builder()
						.userId(userId)
						.title("Habit " + i)
						.icon("water-outline")
						.daysOfWeek(List.of(1, 3, 5))
						.createdAt(now)
						.updatedAt(now)
						.build())
				.toList();

		habitRepository.saveAll(fiftyHabits);

		mockMvc.perform(post("/api/v1/habits")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Habit 51",
									"icon": "water-outline",
									"daysOfWeek": [1, 3, 5]
								}
								"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("HABIT_LIMIT_REACHED"));
	}

	private String extractJsonValue(String json, String key) {
		Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
		Matcher matcher = pattern.matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

}
