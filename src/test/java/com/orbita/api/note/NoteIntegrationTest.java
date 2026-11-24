package com.orbita.api.note;

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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NoteIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("Should execute full note CRUD lifecycle, order by createdAt desc (overall and filtered by category), normalize null content, and populate/evict Redis cache")
	void shouldExecuteFullCrudAndCacheLifecycle() throws Exception {
		String userId = "user-" + UUID.randomUUID();
		String token = jwtService.generateAccessToken(userId);
		String allCacheKey = "notes::" + userId + ":ALL";
		String studyCacheKey = "notes::" + userId + ":STUDY";

		// 1. Create three notes sequentially (note 1 without content -> normalized to "")
		MvcResult createFirst = mockMvc.perform(post("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "  Zebra Study Note 1  ",
									"category": "estudo"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Zebra Study Note 1"))
				.andExpect(jsonPath("$.category").value("estudo"))
				.andExpect(jsonPath("$.content").value(""))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andReturn();

		String noteId1 = extractJsonValue(createFirst.getResponse().getContentAsString(), "id");

		Thread.sleep(10);

		MvcResult createSecond = mockMvc.perform(post("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Alpha Work Note",
									"category": "trabalho",
									"content": "Meeting summary"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String noteId2 = extractJsonValue(createSecond.getResponse().getContentAsString(), "id");

		Thread.sleep(10);

		MvcResult createThird = mockMvc.perform(post("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Beta Study Note 2",
									"category": "estudo",
									"content": "Data structures"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String noteId3 = extractJsonValue(createThird.getResponse().getContentAsString(), "id");

		// 2. List all notes -> strictly ordered by createdAt desc (noteId3, noteId2, noteId1), never alphabetically or grouped
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(noteId3))
				.andExpect(jsonPath("$[1].id").value(noteId2))
				.andExpect(jsonPath("$[2].id").value(noteId1));

		assertThat(redisTemplate.hasKey(allCacheKey)).isTrue();

		// 2b. Second unfiltered GET -> served directly from Redis cache hit
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(noteId3))
				.andExpect(jsonPath("$[1].id").value(noteId2))
				.andExpect(jsonPath("$[2].id").value(noteId1));

		// 3. List notes filtered by ?category=estudo -> ordered by createdAt desc (noteId3 then noteId1) and cached separately
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.param("category", "estudo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(noteId3))
				.andExpect(jsonPath("$[1].id").value(noteId1));

		assertThat(redisTemplate.hasKey(studyCacheKey)).isTrue();

		// 3b. Second filtered GET -> served from Redis cache hit
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.param("category", "estudo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(noteId3))
				.andExpect(jsonPath("$[1].id").value(noteId1));

		// 4. PUT update note 1 -> updates title, category, content, and evicts all notes cache entries
		mockMvc.perform(put("/api/v1/notes/{id}", noteId1)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Updated Personal Note",
									"category": "pessoal",
									"content": "# Grocery List"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(noteId1))
				.andExpect(jsonPath("$.title").value("Updated Personal Note"))
				.andExpect(jsonPath("$.category").value("pessoal"))
				.andExpect(jsonPath("$.content").value("# Grocery List"));

		assertThat(redisTemplate.hasKey(allCacheKey)).isFalse();
		assertThat(redisTemplate.hasKey(studyCacheKey)).isFalse();

		// 5. Delete note 2 -> 204 No Content
		mockMvc.perform(delete("/api/v1/notes/{id}", noteId2)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		// 6. List again -> only note 3 and updated note 1 remain in createdAt desc order
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(noteId3))
				.andExpect(jsonPath("$[1].id").value(noteId1))
				.andExpect(jsonPath("$[1].category").value("pessoal"));
	}

	@Test
	@DisplayName("Should enforce tenant isolation: User B cannot read, update, or delete User A's notes")
	void shouldEnforceTenantIsolation() throws Exception {
		String tokenA = jwtService.generateAccessToken("user-A-" + UUID.randomUUID());
		String tokenB = jwtService.generateAccessToken("user-B-" + UUID.randomUUID());

		MvcResult created = mockMvc.perform(post("/api/v1/notes")
						.header("Authorization", "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "User A Secret Note",
									"category": "pessoal",
									"content": "Private thoughts"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String noteAId = extractJsonValue(created.getResponse().getContentAsString(), "id");

		// User B lists notes -> sees empty list
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// User B tries to update User A's note -> 404 NOTE_NOT_FOUND
		mockMvc.perform(put("/api/v1/notes/{id}", noteAId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "Hijacked",
									"category": "trabalho",
									"content": "Overwritten"
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));

		// User B tries to delete User A's note -> 404 NOTE_NOT_FOUND
		mockMvc.perform(delete("/api/v1/notes/{id}", noteAId)
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
	}

	@Test
	@DisplayName("Should return 400 VALIDATION_ERROR on invalid fields/category param and 401 UNAUTHORIZED when token is missing")
	void shouldValidateInputsAndRequireAuth() throws Exception {
		String token = jwtService.generateAccessToken("user-val-" + UUID.randomUUID());

		// Missing required title -> 400 VALIDATION_ERROR with REQUIRED
		mockMvc.perform(post("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"title": "   ",
									"category": "estudo",
									"content": "Content"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("title"))
				.andExpect(jsonPath("$.details[0].code").value("REQUIRED"));

		// Invalid category query parameter -> 400 VALIDATION_ERROR with INVALID_VALUE
		mockMvc.perform(get("/api/v1/notes")
						.header("Authorization", "Bearer " + token)
						.param("category", "invalida"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("category"))
				.andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"));

		// Unauthenticated request -> 401 UNAUTHORIZED
		mockMvc.perform(get("/api/v1/notes"))
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
