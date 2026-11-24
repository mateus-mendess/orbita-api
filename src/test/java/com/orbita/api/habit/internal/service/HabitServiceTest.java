package com.orbita.api.habit.internal.service;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.habit.internal.dto.HabitCompletionRequest;
import com.orbita.api.habit.internal.dto.HabitRequest;
import com.orbita.api.habit.internal.dto.HabitResponse;
import com.orbita.api.habit.internal.mapper.HabitMapper;
import com.orbita.api.habit.internal.model.Habit;
import com.orbita.api.habit.internal.repository.HabitRepository;
import com.orbita.api.habit.internal.validator.HabitValidator;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

	@Mock
	private HabitRepository habitRepository;

	private HabitService habitService;

	@BeforeEach
	void setUp() {
		HabitMapper habitMapper = Mappers.getMapper(HabitMapper.class);
		habitService = new HabitService(habitRepository, new HabitValidator(), habitMapper);
	}

	@Test
	@DisplayName("Should create habit with trimmed title, sorted daysOfWeek, frequencyDaysPerWeek, empty completedDates, and timestamps")
	void shouldCreateHabit() {
		HabitRequest request = new HabitRequest(
				"  Drink 2L Water  ",
				"water-outline",
				List.of(5, 1, 3)
		);

		when(habitRepository.countByUserId("user-123")).thenReturn(10L);
		when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> {
			Habit arg = invocation.getArgument(0);
			arg.setId("habit-1");
			return arg;
		});

		HabitResponse response = habitService.create("user-123", request);

		assertThat(response.id()).isEqualTo("habit-1");
		assertThat(response.title()).isEqualTo("Drink 2L Water");
		assertThat(response.icon()).isEqualTo("water-outline");
		assertThat(response.daysOfWeek()).containsExactly(1, 3, 5);
		assertThat(response.frequencyDaysPerWeek()).isEqualTo(3);
		assertThat(response.completedDates()).isEmpty();
		assertThat(response.createdAt()).isNotNull();

		ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
		verify(habitRepository).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo("user-123");
		assertThat(captor.getValue().getCreatedAt()).isNotNull();
		assertThat(captor.getValue().getUpdatedAt()).isNotNull();
	}

	@Test
	@DisplayName("Should throw 422 HABIT_LIMIT_REACHED when user already has 50 habits")
	void shouldThrowWhenHabitLimitReached() {
		HabitRequest request = new HabitRequest(
				"Read Book",
				"book-outline",
				List.of(0, 6)
		);

		when(habitRepository.countByUserId("user-123")).thenReturn(50L);

		assertThatThrownBy(() -> habitService.create("user-123", request))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.HABIT_LIMIT_REACHED);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
				});
	}

	@Test
	@DisplayName("Should reject habit creation when daysOfWeek contains duplicates or out-of-range values")
	void shouldRejectInvalidDaysOfWeek() {
		HabitRequest duplicateDaysRequest = new HabitRequest(
				"Gym",
				"barbell-outline",
				List.of(1, 1, 3)
		);

		assertThatThrownBy(() -> habitService.create("user-123", duplicateDaysRequest))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("daysOfWeek");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		HabitRequest outOfRangeRequest = new HabitRequest(
				"Gym",
				"barbell-outline",
				List.of(0, 7)
		);

		assertThatThrownBy(() -> habitService.create("user-123", outOfRangeRequest))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("daysOfWeek");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		verifyNoInteractions(habitRepository);
	}

	@Test
	@DisplayName("Should list all habits for user ordered by newest first")
	void shouldListHabits() {
		Habit habit = Habit.builder()
				.id("habit-1")
				.userId("user-123")
				.title("Meditation")
				.icon("leaf-outline")
				.daysOfWeek(List.of(0, 1, 2, 3, 4, 5, 6))
				.completedDates(List.of("2026-11-23"))
				.createdAt(Instant.now())
				.build();

		when(habitRepository.findAllByUserIdOrderByCreatedAtDesc("user-123"))
				.thenReturn(List.of(habit));

		List<HabitResponse> result = habitService.listAll("user-123");

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().id()).isEqualTo("habit-1");
		assertThat(result.getFirst().frequencyDaysPerWeek()).isEqualTo(7);
		assertThat(result.getFirst().completedDates()).containsExactly("2026-11-23");
	}

	@Test
	@DisplayName("Should update existing habit while preserving existing completedDates")
	void shouldUpdateHabitPreservingCompletedDates() {
		Habit existing = Habit.builder()
				.id("habit-1")
				.userId("user-123")
				.title("Old Title")
				.icon("water-outline")
				.daysOfWeek(List.of(1, 3))
				.completedDates(new ArrayList<>(List.of("2026-11-20", "2026-11-22")))
				.createdAt(Instant.now())
				.build();

		when(habitRepository.findByIdAndUserId("habit-1", "user-123")).thenReturn(Optional.of(existing));
		when(habitRepository.save(existing)).thenReturn(existing);

		HabitRequest updateRequest = new HabitRequest(
				"  Updated Habit  ",
				"fitness-outline",
				List.of(6, 2, 4, 0)
		);

		HabitResponse response = habitService.update("user-123", "habit-1", updateRequest);

		assertThat(response.title()).isEqualTo("Updated Habit");
		assertThat(response.icon()).isEqualTo("fitness-outline");
		assertThat(response.daysOfWeek()).containsExactly(0, 2, 4, 6);
		assertThat(response.frequencyDaysPerWeek()).isEqualTo(4);
		assertThat(response.completedDates()).containsExactly("2026-11-20", "2026-11-22");
	}

	@Test
	@DisplayName("Should idempotently mark and unmark completion dates freely")
	void shouldMarkAndUnmarkCompletionIdempotently() {
		Habit existing = Habit.builder()
				.id("habit-1")
				.userId("user-123")
				.title("Walk")
				.icon("walk-outline")
				.daysOfWeek(List.of(1, 2, 3))
				.completedDates(new ArrayList<>(List.of("2026-11-22")))
				.createdAt(Instant.now())
				.build();

		when(habitRepository.findByIdAndUserId("habit-1", "user-123")).thenReturn(Optional.of(existing));
		when(habitRepository.save(existing)).thenReturn(existing);

		// Mark 2026-11-20 -> added and sorted
		HabitResponse afterMark = habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest("2026-11-20", true)
		);
		assertThat(afterMark.completedDates()).containsExactly("2026-11-20", "2026-11-22");

		// Mark 2026-11-20 again -> idempotent, no duplicate
		HabitResponse afterSecondMark = habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest("2026-11-20", true)
		);
		assertThat(afterSecondMark.completedDates()).containsExactly("2026-11-20", "2026-11-22");

		// Unmark 2026-11-20 -> removed
		HabitResponse afterUnmark = habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest("2026-11-20", false)
		);
		assertThat(afterUnmark.completedDates()).containsExactly("2026-11-22");

		// Unmark 2026-11-20 again -> idempotent
		HabitResponse afterSecondUnmark = habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest("2026-11-20", false)
		);
		assertThat(afterSecondUnmark.completedDates()).containsExactly("2026-11-22");
	}

	@Test
	@DisplayName("Should reject completion date when invalid format or more than 1 day in the future")
	void shouldRejectInvalidOrFarFutureCompletionDate() {
		assertThatThrownBy(() -> habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest("2026-02-30", true)
		))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("date");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		String twoDaysInFuture = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
		assertThatThrownBy(() -> habitService.updateCompletion(
				"user-123",
				"habit-1",
				new HabitCompletionRequest(twoDaysInFuture, true)
		))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("date");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		verifyNoInteractions(habitRepository);
	}

	@Test
	@DisplayName("Should throw 404 HABIT_NOT_FOUND on update, completion, or delete when habit is missing or belongs to another user")
	void shouldThrowNotFoundOnMissingOrForeignHabit() {
		when(habitRepository.findByIdAndUserId("habit-999", "user-123")).thenReturn(Optional.empty());

		HabitRequest request = new HabitRequest("Read", "book-outline", List.of(1));

		assertThatThrownBy(() -> habitService.update("user-123", "habit-999", request))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.HABIT_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});

		assertThatThrownBy(() -> habitService.updateCompletion("user-123", "habit-999", new HabitCompletionRequest("2026-11-23", true)))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.HABIT_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});

		assertThatThrownBy(() -> habitService.delete("user-123", "habit-999"))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.HABIT_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});
	}

	@Test
	@DisplayName("Should delete habit when owned by user")
	void shouldDeleteHabit() {
		Habit existing = Habit.builder().id("habit-1").userId("user-123").build();
		when(habitRepository.findByIdAndUserId("habit-1", "user-123")).thenReturn(Optional.of(existing));

		habitService.delete("user-123", "habit-1");

		verify(habitRepository).delete(existing);
	}

}
