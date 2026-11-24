package com.orbita.api.task.internal.controller;

import com.orbita.api.task.internal.dto.TaskCompletionRequest;
import com.orbita.api.task.internal.dto.TaskRequest;
import com.orbita.api.task.internal.dto.TaskResponse;
import com.orbita.api.task.internal.service.TaskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tasks", description = "Daily task management")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

	private final TaskService taskService;

	@Operation(summary = "List tasks within a date range ordered by date and startTime")
	@GetMapping
	public ResponseEntity<List<TaskResponse>> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam String from,
			@RequestParam String to
	) {
		return ResponseEntity.ok(taskService.listByDateRange(jwt.getSubject(), from, to));
	}

	@Operation(summary = "Create a new task")
	@PostMapping
	public ResponseEntity<TaskResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody TaskRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(jwt.getSubject(), request));
	}

	@Operation(summary = "Replace an existing task")
	@PutMapping("/{id}")
	public ResponseEntity<TaskResponse> update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id,
			@Valid @RequestBody TaskRequest request
	) {
		return ResponseEntity.ok(taskService.update(jwt.getSubject(), id, request));
	}

	@Operation(summary = "Update a task's completion status")
	@PatchMapping("/{id}/completed")
	public ResponseEntity<TaskResponse> updateCompletion(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id,
			@Valid @RequestBody TaskCompletionRequest request
	) {
		return ResponseEntity.ok(taskService.updateCompletion(jwt.getSubject(), id, request));
	}

	@Operation(summary = "Delete an existing task")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id
	) {
		taskService.delete(jwt.getSubject(), id);
		return ResponseEntity.noContent().build();
	}

}
