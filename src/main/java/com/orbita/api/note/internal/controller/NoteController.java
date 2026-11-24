package com.orbita.api.note.internal.controller;

import com.orbita.api.common.model.Category;
import com.orbita.api.note.internal.dto.NoteRequest;
import com.orbita.api.note.internal.dto.NoteResponse;
import com.orbita.api.note.internal.service.NoteService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notes", description = "Markdown note management")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

	private final NoteService noteService;

	@Operation(summary = "List notes for the authenticated user ordered by newest first, optionally filtered by category")
	@GetMapping
	public ResponseEntity<List<NoteResponse>> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Category category
	) {
		return ResponseEntity.ok(noteService.listAll(jwt.getSubject(), category));
	}

	@Operation(summary = "Create a new note")
	@PostMapping
	public ResponseEntity<NoteResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody NoteRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(jwt.getSubject(), request));
	}

	@Operation(summary = "Replace an existing note")
	@PutMapping("/{id}")
	public ResponseEntity<NoteResponse> update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id,
			@Valid @RequestBody NoteRequest request
	) {
		return ResponseEntity.ok(noteService.update(jwt.getSubject(), id, request));
	}

	@Operation(summary = "Delete an existing note")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable String id
	) {
		noteService.delete(jwt.getSubject(), id);
		return ResponseEntity.noContent().build();
	}

}
