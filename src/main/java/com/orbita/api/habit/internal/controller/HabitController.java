package com.orbita.api.habit.internal.controller;

import com.orbita.api.habit.internal.dto.HabitCompletionRequest;
import com.orbita.api.habit.internal.dto.HabitRequest;
import com.orbita.api.habit.internal.dto.HabitResponse;
import com.orbita.api.habit.internal.service.HabitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Habits", description = "Habit tracking management")
@RestController
@RequestMapping("/api/v1/habits")
@RequiredArgsConstructor
public class HabitController {

	private final HabitService habitService;

	@Operation(summary = "List all habits for the authenticated user ordered by newest first")
	@GetMapping
	public ResponseEntity<List<HabitResponse>> list(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(habitService.listAll(jwt.getSubject()));
	}

	@Operation(summary = "Create a new habit")
	@PostMapping
	public ResponseEntity<HabitResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody HabitRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(habitService.create(jwt.getSubject(), request));
	}

	@Operation(summary = "Replace an existing habit while preserving its completedDates")
	@PutMapping("/{id}")
	public ResponseEntity<HabitResponse> update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id,
			@Valid @RequestBody HabitRequest request
	) {
		return ResponseEntity.ok(habitService.update(jwt.getSubject(), id, request));
	}

	@Operation(summary = "Idempotently mark or unmark a habit completion date")
	@PatchMapping("/{id}/completed")
	public ResponseEntity<HabitResponse> updateCompletion(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id,
			@Valid @RequestBody HabitCompletionRequest request
	) {
		return ResponseEntity.ok(habitService.updateCompletion(jwt.getSubject(), id, request));
	}

	@Operation(summary = "Delete an existing habit")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id
	) {
		habitService.delete(jwt.getSubject(), id);
		return ResponseEntity.noContent().build();
	}

}
