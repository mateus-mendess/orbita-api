package com.orbita.api.note.internal.service;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.model.Category;
import com.orbita.api.note.internal.dto.NoteRequest;
import com.orbita.api.note.internal.dto.NoteResponse;
import com.orbita.api.note.internal.mapper.NoteMapper;
import com.orbita.api.note.internal.model.Note;
import com.orbita.api.note.internal.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

	public static final String NOTES_CACHE = "notes";

	private final NoteRepository noteRepository;
	private final NoteMapper noteMapper;

	@Cacheable(value = NOTES_CACHE, key = "#userId + ':' + (#category != null ? #category.name() : 'ALL')")
	public List<NoteResponse> listAll(String userId, Category category) {
		List<Note> notes = category != null
				? noteRepository.findAllByUserIdAndCategoryOrderByCreatedAtDesc(userId, category)
				: noteRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

		return notes.stream()
				.map(noteMapper::toResponse)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	@CacheEvict(value = NOTES_CACHE, allEntries = true)
	public NoteResponse create(String userId, NoteRequest request) {
		validateTitle(request.title());

		Instant now = Instant.now();
		Note note = noteMapper.toEntity(request);
		note.setUserId(userId);
		note.setTitle(request.title().trim());
		note.setContent(request.content() != null ? request.content() : "");
		note.setCreatedAt(now);
		note.setUpdatedAt(now);

		Note saved = noteRepository.save(note);
		return noteMapper.toResponse(saved);
	}

	@CacheEvict(value = NOTES_CACHE, allEntries = true)
	public NoteResponse update(String userId, String noteId, NoteRequest request) {
		validateTitle(request.title());

		Note existing = noteRepository.findByIdAndUserId(noteId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.NOTE_NOT_FOUND, HttpStatus.NOT_FOUND));

		existing.setTitle(request.title().trim());
		existing.setCategory(request.category());
		existing.setContent(request.content() != null ? request.content() : "");
		existing.setUpdatedAt(Instant.now());

		Note saved = noteRepository.save(existing);
		return noteMapper.toResponse(saved);
	}

	@CacheEvict(value = NOTES_CACHE, allEntries = true)
	public void delete(String userId, String noteId) {
		Note existing = noteRepository.findByIdAndUserId(noteId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.NOTE_NOT_FOUND, HttpStatus.NOT_FOUND));

		noteRepository.delete(existing);
	}

	private void validateTitle(String title) {
		if (title == null || title.trim().isEmpty()) {
			throw new FieldValidationException("title", ErrorCode.REQUIRED);
		}
	}

}
