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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

	@Mock
	private NoteRepository noteRepository;

	private NoteService noteService;

	@BeforeEach
	void setUp() {
		NoteMapper noteMapper = Mappers.getMapper(NoteMapper.class);
		noteService = new NoteService(noteRepository, noteMapper);
	}

	@Test
	@DisplayName("Should create note with trimmed title, userId, content, and timestamps")
	void shouldCreateNote() {
		NoteRequest request = new NoteRequest(
				"  Architecture Notes  ",
				Category.WORK,
				"# Spring Modulith\nKeep modules decoupled."
		);

		when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> {
			Note arg = invocation.getArgument(0);
			arg.setId("note-1");
			return arg;
		});

		NoteResponse response = noteService.create("user-123", request);

		assertThat(response.id()).isEqualTo("note-1");
		assertThat(response.title()).isEqualTo("Architecture Notes");
		assertThat(response.category()).isEqualTo(Category.WORK);
		assertThat(response.content()).isEqualTo("# Spring Modulith\nKeep modules decoupled.");
		assertThat(response.createdAt()).isNotNull();
		assertThat(response.updatedAt()).isNotNull();

		ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
		verify(noteRepository).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo("user-123");
	}

	@Test
	@DisplayName("Should normalize null content to empty string on create and update")
	void shouldNormalizeNullContentToEmptyString() {
		NoteRequest createRequest = new NoteRequest("Quick Idea", Category.PERSONAL, null);

		when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> {
			Note arg = invocation.getArgument(0);
			arg.setId("note-1");
			return arg;
		});

		NoteResponse created = noteService.create("user-123", createRequest);
		assertThat(created.content()).isEmpty();

		Note existing = Note.builder()
				.id("note-1")
				.userId("user-123")
				.title("Quick Idea")
				.category(Category.PERSONAL)
				.content("Previous content")
				.createdAt(Instant.now())
				.updatedAt(Instant.now())
				.build();

		when(noteRepository.findByIdAndUserId("note-1", "user-123")).thenReturn(Optional.of(existing));

		NoteResponse updated = noteService.update("user-123", "note-1", new NoteRequest("Quick Idea", Category.PERSONAL, null));
		assertThat(updated.content()).isEmpty();
	}

	@Test
	@DisplayName("Should reject create or update when trimmed title is empty")
	void shouldRejectBlankTitle() {
		NoteRequest request = new NoteRequest("   ", Category.STUDY, "content");

		assertThatThrownBy(() -> noteService.create("user-123", request))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("title");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.REQUIRED.name());
				});

		verifyNoInteractions(noteRepository);
	}

	@Test
	@DisplayName("Should list all notes ordered by createdAt desc when category is null, and filter by category when provided")
	void shouldListNotesUnfilteredAndFilteredByCategory() {
		Note workNote = Note.builder()
				.id("note-work")
				.userId("user-123")
				.title("Work Note")
				.category(Category.WORK)
				.content("Sprint planning")
				.createdAt(Instant.now())
				.updatedAt(Instant.now())
				.build();

		Note studyNote = Note.builder()
				.id("note-study")
				.userId("user-123")
				.title("Study Note")
				.category(Category.STUDY)
				.content("Graphs")
				.createdAt(Instant.now().minusSeconds(60))
				.updatedAt(Instant.now().minusSeconds(60))
				.build();

		when(noteRepository.findAllByUserIdOrderByCreatedAtDesc("user-123"))
				.thenReturn(List.of(workNote, studyNote));
		when(noteRepository.findAllByUserIdAndCategoryOrderByCreatedAtDesc("user-123", Category.STUDY))
				.thenReturn(List.of(studyNote));

		List<NoteResponse> allNotes = noteService.listAll("user-123", null);
		assertThat(allNotes).hasSize(2);
		assertThat(allNotes.get(0).id()).isEqualTo("note-work");
		assertThat(allNotes.get(1).id()).isEqualTo("note-study");

		List<NoteResponse> studyNotes = noteService.listAll("user-123", Category.STUDY);
		assertThat(studyNotes).hasSize(1);
		assertThat(studyNotes.getFirst().id()).isEqualTo("note-study");
		assertThat(studyNotes.getFirst().category()).isEqualTo(Category.STUDY);
	}

	@Test
	@DisplayName("Should update existing note while preserving createdAt and updating updatedAt")
	void shouldUpdateNote() {
		Instant initialCreatedAt = Instant.now().minusSeconds(3600);
		Note existing = Note.builder()
				.id("note-1")
				.userId("user-123")
				.title("Old Title")
				.category(Category.PERSONAL)
				.content("Old content")
				.createdAt(initialCreatedAt)
				.updatedAt(initialCreatedAt)
				.build();

		when(noteRepository.findByIdAndUserId("note-1", "user-123")).thenReturn(Optional.of(existing));
		when(noteRepository.save(existing)).thenReturn(existing);

		NoteRequest updateRequest = new NoteRequest(
				"  Updated Title  ",
				Category.STUDY,
				"Updated markdown content"
		);

		NoteResponse response = noteService.update("user-123", "note-1", updateRequest);

		assertThat(response.title()).isEqualTo("Updated Title");
		assertThat(response.category()).isEqualTo(Category.STUDY);
		assertThat(response.content()).isEqualTo("Updated markdown content");
		assertThat(response.createdAt()).isEqualTo(initialCreatedAt);
		assertThat(response.updatedAt()).isAfter(initialCreatedAt);
	}

	@Test
	@DisplayName("Should throw 404 NOTE_NOT_FOUND on update or delete when note is missing or belongs to another user")
	void shouldThrowNotFoundOnMissingOrForeignNote() {
		when(noteRepository.findByIdAndUserId("note-999", "user-123")).thenReturn(Optional.empty());

		NoteRequest request = new NoteRequest("Title", Category.WORK, "Content");

		assertThatThrownBy(() -> noteService.update("user-123", "note-999", request))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.NOTE_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});

		assertThatThrownBy(() -> noteService.delete("user-123", "note-999"))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.NOTE_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});
	}

	@Test
	@DisplayName("Should delete note when owned by user")
	void shouldDeleteNote() {
		Note existing = Note.builder().id("note-1").userId("user-123").build();
		when(noteRepository.findByIdAndUserId("note-1", "user-123")).thenReturn(Optional.of(existing));

		noteService.delete("user-123", "note-1");

		verify(noteRepository).delete(existing);
	}

}
