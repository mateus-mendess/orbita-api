package com.orbita.api.task.internal.service;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.common.model.Category;
import com.orbita.api.task.internal.dto.TaskCompletionRequest;
import com.orbita.api.task.internal.dto.TaskRequest;
import com.orbita.api.task.internal.dto.TaskResponse;
import com.orbita.api.task.internal.mapper.TaskMapper;
import com.orbita.api.task.internal.model.Task;
import com.orbita.api.task.internal.model.Urgency;
import com.orbita.api.task.internal.repository.TaskRepository;
import com.orbita.api.task.internal.validator.TaskValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

	@Mock
	private TaskRepository taskRepository;

	private TaskService taskService;

	@BeforeEach
	void setUp() {
		TaskMapper taskMapper = Mappers.getMapper(TaskMapper.class);
		taskService = new TaskService(taskRepository, new TaskValidator(), taskMapper);
	}

	@Test
	@DisplayName("Should create task with trimmed title, userId, completed false, and timestamps")
	void shouldCreateTask() {
		TaskRequest request = new TaskRequest(
				"  Study Spring Modulith  ",
				"Read chapter 3",
				"2026-11-23",
				"09:00",
				"10:30",
				Urgency.URGENT,
				Category.STUDY
		);

		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
			Task arg = invocation.getArgument(0);
			arg.setId("task-1");
			return arg;
		});

		TaskResponse response = taskService.create("user-123", request);

		assertThat(response.id()).isEqualTo("task-1");
		assertThat(response.title()).isEqualTo("Study Spring Modulith");
		assertThat(response.description()).isEqualTo("Read chapter 3");
		assertThat(response.date()).isEqualTo("2026-11-23");
		assertThat(response.startTime()).isEqualTo("09:00");
		assertThat(response.endTime()).isEqualTo("10:30");
		assertThat(response.urgency()).isEqualTo(Urgency.URGENT);
		assertThat(response.category()).isEqualTo(Category.STUDY);
		assertThat(response.completed()).isFalse();

		ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
		verify(taskRepository).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo("user-123");
		assertThat(captor.getValue().getCreatedAt()).isNotNull();
		assertThat(captor.getValue().getUpdatedAt()).isNotNull();
	}

	@ParameterizedTest
	@CsvSource({
			"09:00, 09:00",
			"10:30, 09:00"
	})
	@DisplayName("Should reject task creation when endTime is not after startTime")
	void shouldRejectWhenEndTimeIsNotAfterStartTime(String startTime, String endTime) {
		TaskRequest request = new TaskRequest(
				"Meeting",
				null,
				"2026-11-23",
				startTime,
				endTime,
				Urgency.LOW_URGENT,
				Category.WORK
		);

		assertThatThrownBy(() -> taskService.create("user-123", request))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("endTime");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_TIME_RANGE.name());
				});

		verifyNoInteractions(taskRepository);
	}

	@ParameterizedTest
	@CsvSource({
			"2026-13-01, 09:00, 10:00, date",
			"2026-11-23, 25:00, 10:00, startTime",
			"2026-11-23, 09:00, 99:99, endTime"
	})
	@DisplayName("Should reject task creation when date or time format is invalid")
	void shouldRejectInvalidDateOrTimeFormat(String date, String startTime, String endTime, String expectedField) {
		TaskRequest request = new TaskRequest(
				"Meeting",
				null,
				date,
				startTime,
				endTime,
				Urgency.NOT_URGENT,
				Category.PERSONAL
		);

		assertThatThrownBy(() -> taskService.create("user-123", request))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo(expectedField);
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		verifyNoInteractions(taskRepository);
	}

	@Test
	@DisplayName("Should list tasks by date range for user")
	void shouldListTasksByDateRange() {
		Task task = Task.builder()
				.id("task-1")
				.userId("user-123")
				.title("Workout")
				.date("2026-11-23")
				.startTime("07:00")
				.endTime("08:00")
				.urgency(Urgency.NOT_URGENT)
				.category(Category.PERSONAL)
				.completed(true)
				.build();

		when(taskRepository.findAllByUserIdAndDateRange("user-123", "2026-11-01", "2026-11-30"))
				.thenReturn(List.of(task));

		List<TaskResponse> result = taskService.listByDateRange("user-123", "2026-11-01", "2026-11-30");

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().id()).isEqualTo("task-1");
		assertThat(result.getFirst().completed()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"2026-11-30, 2026-11-01",
			"2025-01-01, 2026-06-01"
	})
	@DisplayName("Should reject list query when date range is inverted or exceeds 366 days")
	void shouldRejectInvalidDateRangeOnList(String from, String to) {
		assertThatThrownBy(() -> taskService.listByDateRange("user-123", from, to))
				.isInstanceOf(FieldValidationException.class)
				.satisfies(ex -> {
					FieldValidationException fieldEx = (FieldValidationException) ex;
					assertThat(fieldEx.getDetails()).hasSize(1);
					assertThat(fieldEx.getDetails().getFirst().field()).isEqualTo("to");
					assertThat(fieldEx.getDetails().getFirst().code()).isEqualTo(ErrorCode.INVALID_VALUE.name());
				});

		verifyNoInteractions(taskRepository);
	}

	@Test
	@DisplayName("Should update existing task owned by user while preserving existing completed flag")
	void shouldUpdateTaskPreservingCompleted() {
		Task existing = Task.builder()
				.id("task-1")
				.userId("user-123")
				.title("Old Title")
				.date("2026-11-23")
				.startTime("08:00")
				.endTime("09:00")
				.urgency(Urgency.NOT_URGENT)
				.category(Category.PERSONAL)
				.completed(true)
				.build();

		when(taskRepository.findByIdAndUserId("task-1", "user-123")).thenReturn(Optional.of(existing));
		when(taskRepository.save(existing)).thenReturn(existing);

		TaskRequest updateRequest = new TaskRequest(
				"  Updated Title  ",
				"Updated description",
				"2026-11-24",
				"14:00",
				"15:30",
				Urgency.VERY_URGENT,
				Category.WORK
		);

		TaskResponse response = taskService.update("user-123", "task-1", updateRequest);

		assertThat(response.title()).isEqualTo("Updated Title");
		assertThat(response.description()).isEqualTo("Updated description");
		assertThat(response.date()).isEqualTo("2026-11-24");
		assertThat(response.startTime()).isEqualTo("14:00");
		assertThat(response.endTime()).isEqualTo("15:30");
		assertThat(response.urgency()).isEqualTo(Urgency.VERY_URGENT);
		assertThat(response.category()).isEqualTo(Category.WORK);
		assertThat(response.completed()).isTrue();
	}

	@Test
	@DisplayName("Should throw 404 TASK_NOT_FOUND when updating task that does not exist or belongs to another user")
	void shouldThrowNotFoundWhenUpdatingMissingOrForeignTask() {
		when(taskRepository.findByIdAndUserId("task-999", "user-123")).thenReturn(Optional.empty());

		TaskRequest updateRequest = new TaskRequest(
				"Title",
				null,
				"2026-11-23",
				"09:00",
				"10:00",
				Urgency.URGENT,
				Category.WORK
		);

		assertThatThrownBy(() -> taskService.update("user-123", "task-999", updateRequest))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});
	}

	@Test
	@DisplayName("Should update task completion status when owned by user and throw 404 TASK_NOT_FOUND otherwise")
	void shouldUpdateTaskCompletionAndThrowWhenNotFound() {
		Task existing = Task.builder()
				.id("task-1")
				.userId("user-123")
				.title("Title")
				.date("2026-11-23")
				.startTime("09:00")
				.endTime("10:00")
				.urgency(Urgency.URGENT)
				.category(Category.WORK)
				.completed(false)
				.build();

		when(taskRepository.findByIdAndUserId("task-1", "user-123")).thenReturn(Optional.of(existing));
		when(taskRepository.save(existing)).thenReturn(existing);
		when(taskRepository.findByIdAndUserId("task-1", "other-user")).thenReturn(Optional.empty());

		TaskResponse response = taskService.updateCompletion("user-123", "task-1", new TaskCompletionRequest(true));
		assertThat(response.completed()).isTrue();
		assertThat(existing.getUpdatedAt()).isNotNull();

		assertThatThrownBy(() -> taskService.updateCompletion("other-user", "task-1", new TaskCompletionRequest(false)))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});
	}

	@Test
	@DisplayName("Should delete task when owned by user and throw 404 TASK_NOT_FOUND otherwise")
	void shouldDeleteTaskAndThrowWhenNotFound() {
		Task existing = Task.builder().id("task-1").userId("user-123").build();
		when(taskRepository.findByIdAndUserId("task-1", "user-123")).thenReturn(Optional.of(existing));
		when(taskRepository.findByIdAndUserId("task-1", "other-user")).thenReturn(Optional.empty());

		taskService.delete("user-123", "task-1");
		verify(taskRepository).delete(existing);

		assertThatThrownBy(() -> taskService.delete("other-user", "task-1"))
				.isInstanceOf(OrbitaApiException.class)
				.satisfies(ex -> {
					OrbitaApiException appEx = (OrbitaApiException) ex;
					assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
					assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				});
	}

}
