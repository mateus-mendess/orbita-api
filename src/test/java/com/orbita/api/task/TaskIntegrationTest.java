package com.orbita.api.task;

import com.orbita.api.AbstractIntegrationTest;
import com.orbita.api.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("Should execute full task CRUD lifecycle, order by date and startTime, preserve completed on PUT, and populate/evict Redis cache")
	void shouldExecuteFullCrudAndCacheLifecycle() throws Exception {
		String userId = "user-" + UUID.randomUUID();
		String token = jwtService.generateAccessToken(userId);

		// 1. Create two tasks out of chronological order
		MvcResult createSecond = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "  Afternoon Study  ",
									"description": "Review algorithms",
									"date": "2026-11-23",
									"startTime": "14:00",
									"endTime": "16:00",
									"urgency": "urgente",
									"category": "estudo"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Afternoon Study"))
				.andExpect(jsonPath("$.urgency").value("urgente"))
				.andExpect(jsonPath("$.category").value("estudo"))
				.andExpect(jsonPath("$.completed").value(false))
				.andReturn();

		String taskId2 = extractJsonValue(createSecond.getResponse().getContentAsString(), "id");

		MvcResult createFirst = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Morning Workout",
									"date": "2026-11-23",
									"startTime": "07:30",
									"endTime": "08:30",
									"urgency": "pouco_urgente",
									"category": "pessoal"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String taskId1 = extractJsonValue(createFirst.getResponse().getContentAsString(), "id");

		// 2. List tasks on single exact boundary date (from=2026-11-23&to=2026-11-23) -> inclusive $gte/$lte, ordered by date asc, startTime asc, and cached in Redis
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-23")
						.param("to", "2026-11-23"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(taskId1))
				.andExpect(jsonPath("$[0].startTime").value("07:30"))
				.andExpect(jsonPath("$[1].id").value(taskId2))
				.andExpect(jsonPath("$[1].startTime").value("14:00"));

		Set<String> cacheKeysAfterGet = redisTemplate.keys("tasks::" + userId + "*");
		assertThat(cacheKeysAfterGet).isNotEmpty();

		// 2b. Second GET on same boundary date -> served directly from Redis cache hit
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-23")
						.param("to", "2026-11-23"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(taskId1))
				.andExpect(jsonPath("$[0].startTime").value("07:30"))
				.andExpect(jsonPath("$[1].id").value(taskId2))
				.andExpect(jsonPath("$[1].startTime").value("14:00"));

		// 3. PATCH /completed on task 1 -> marks completed=true and evicts Redis cache
		mockMvc.perform(patch("/api/v1/tasks/{id}/completed", taskId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"completed": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(taskId1))
				.andExpect(jsonPath("$.completed").value(true));

		Set<String> cacheKeysAfterPatch = redisTemplate.keys("tasks::" + userId + "*");
		assertThat(cacheKeysAfterPatch).isEmpty();

		// Repopulate cache before testing PUT eviction
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-01")
						.param("to", "2026-11-30"))
				.andExpect(status().isOk());
		assertThat(redisTemplate.keys("tasks::" + userId + "*")).isNotEmpty();

		// 4. PUT update task 1 -> moves date to 2026-11-24, updates fields, preserves existing completed=true, and evicts cache
		mockMvc.perform(put("/api/v1/tasks/{id}", taskId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Morning Run",
									"description": "5km outdoor run",
									"date": "2026-11-24",
									"startTime": "06:30",
									"endTime": "07:30",
									"urgency": "muito_urgente",
									"category": "pessoal"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(taskId1))
				.andExpect(jsonPath("$.title").value("Morning Run"))
				.andExpect(jsonPath("$.date").value("2026-11-24"))
				.andExpect(jsonPath("$.startTime").value("06:30"))
				.andExpect(jsonPath("$.urgency").value("muito_urgente"))
				.andExpect(jsonPath("$.completed").value(true));

		Set<String> cacheKeysAfterPut = redisTemplate.keys("tasks::" + userId + "*");
		assertThat(cacheKeysAfterPut).isEmpty();

		// 4b. List with exact boundary dates (from=2026-11-23&to=2026-11-24) -> includes both boundary dates ordered by date asc
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-23")
						.param("to", "2026-11-24"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(taskId2))
				.andExpect(jsonPath("$[0].date").value("2026-11-23"))
				.andExpect(jsonPath("$[1].id").value(taskId1))
				.andExpect(jsonPath("$[1].date").value("2026-11-24"));

		// 5. Delete task 2 -> 204 No Content
		mockMvc.perform(delete("/api/v1/tasks/{id}", taskId2)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		// 6. List again -> only updated task 1 remains with completed=true preserved
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-23")
						.param("to", "2026-11-24"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(taskId1))
				.andExpect(jsonPath("$[0].completed").value(true));
	}

	@Test
	@DisplayName("Should enforce tenant isolation: User B cannot read, update, complete, or delete User A's tasks")
	void shouldEnforceTenantIsolation() throws Exception {
		String tokenA = jwtService.generateAccessToken("user-A-" + UUID.randomUUID());
		String tokenB = jwtService.generateAccessToken("user-B-" + UUID.randomUUID());

		MvcResult created = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "User A Private Task",
									"date": "2026-11-23",
									"startTime": "09:00",
									"endTime": "10:00",
									"urgency": "nao_urgente",
									"category": "trabalho"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String taskAId = extractJsonValue(created.getResponse().getContentAsString(), "id");

		// User B lists tasks in the same date range -> sees empty list
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + tokenB)
						.param("from", "2026-11-01")
						.param("to", "2026-11-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// User B tries to update User A's task -> 404 TASK_NOT_FOUND
		mockMvc.perform(put("/api/v1/tasks/{id}", taskAId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Hijacked",
									"date": "2026-11-23",
									"startTime": "09:00",
									"endTime": "10:00",
									"urgency": "urgente",
									"category": "trabalho"
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		// User B tries to toggle completion on User A's task -> 404 TASK_NOT_FOUND
		mockMvc.perform(patch("/api/v1/tasks/{id}/completed", taskAId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"completed": true
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

		// User B tries to delete User A's task -> 404 TASK_NOT_FOUND
		mockMvc.perform(delete("/api/v1/tasks/{id}", taskAId)
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	@Test
	@DisplayName("Should return 400 VALIDATION_ERROR on invalid fields/time range and 401 UNAUTHORIZED when token is missing")
	void shouldValidateInputsAndRequireAuth() throws Exception {
		String token = jwtService.generateAccessToken("user-val-" + UUID.randomUUID());

		// endTime before startTime -> 400 VALIDATION_ERROR with INVALID_TIME_RANGE
		mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Invalid Schedule",
									"date": "2026-11-23",
									"startTime": "15:00",
									"endTime": "14:00",
									"urgency": "urgente",
									"category": "trabalho"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("endTime"))
				.andExpect(jsonPath("$.details[0].code").value("INVALID_TIME_RANGE"));

		// Missing required completed field in PATCH /completed -> 400 VALIDATION_ERROR with REQUIRED
		mockMvc.perform(patch("/api/v1/tasks/{id}/completed", "task-123")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("completed"))
				.andExpect(jsonPath("$.details[0].code").value("REQUIRED"));

		// Missing required query parameter 'to'
		mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", "Bearer " + token)
						.param("from", "2026-11-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("to"))
				.andExpect(jsonPath("$.details[0].code").value("REQUIRED"));

		// Unauthenticated request -> 401 UNAUTHORIZED
		mockMvc.perform(get("/api/v1/tasks")
						.param("from", "2026-11-01")
						.param("to", "2026-11-30"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private String extractJsonValue(String json, String key) {
		Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
		Matcher matcher = pattern.matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

}
