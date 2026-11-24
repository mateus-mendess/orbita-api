package com.orbita.api.task.internal.service;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.task.internal.dto.TaskCompletionRequest;
import com.orbita.api.task.internal.dto.TaskRequest;
import com.orbita.api.task.internal.dto.TaskResponse;
import com.orbita.api.task.internal.mapper.TaskMapper;
import com.orbita.api.task.internal.model.Task;
import com.orbita.api.task.internal.repository.TaskRepository;
import com.orbita.api.task.internal.validator.TaskValidator;
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
public class TaskService {

	public static final String TASKS_CACHE = "tasks";

	private final TaskRepository taskRepository;
	private final TaskValidator taskValidator;
	private final TaskMapper taskMapper;

	@Cacheable(value = TASKS_CACHE, key = "#userId + ':' + #from + ':' + #to")
	public List<TaskResponse> listByDateRange(String userId, String from, String to) {
		taskValidator.validateDateRange(from, to);

		return taskRepository.findAllByUserIdAndDateRange(userId, from, to)
				.stream()
				.map(taskMapper::toResponse)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	@CacheEvict(value = TASKS_CACHE, allEntries = true)
	public TaskResponse create(String userId, TaskRequest request) {
		taskValidator.validateTaskSchedule(request.title(), request.date(), request.startTime(), request.endTime());

		Instant now = Instant.now();
		Task task = taskMapper.toEntity(request);
		task.setUserId(userId);
		task.setTitle(request.title().trim());
		task.setCreatedAt(now);
		task.setUpdatedAt(now);

		Task saved = taskRepository.save(task);
		return taskMapper.toResponse(saved);
	}

	@CacheEvict(value = TASKS_CACHE, allEntries = true)
	public TaskResponse update(String userId, String taskId, TaskRequest request) {
		taskValidator.validateTaskSchedule(request.title(), request.date(), request.startTime(), request.endTime());

		Task existing = taskRepository.findByIdAndUserId(taskId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND));

		existing.setTitle(request.title().trim());
		existing.setDescription(request.description());
		existing.setDate(request.date());
		existing.setStartTime(request.startTime());
		existing.setEndTime(request.endTime());
		existing.setUrgency(request.urgency());
		existing.setCategory(request.category());
		existing.setUpdatedAt(Instant.now());

		Task saved = taskRepository.save(existing);
		return taskMapper.toResponse(saved);
	}

	@CacheEvict(value = TASKS_CACHE, allEntries = true)
	public TaskResponse updateCompletion(String userId, String taskId, TaskCompletionRequest request) {
		Task existing = taskRepository.findByIdAndUserId(taskId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND));

		existing.setCompleted(request.completed());
		existing.setUpdatedAt(Instant.now());

		Task saved = taskRepository.save(existing);
		return taskMapper.toResponse(saved);
	}

	@CacheEvict(value = TASKS_CACHE, allEntries = true)
	public void delete(String userId, String taskId) {
		Task existing = taskRepository.findByIdAndUserId(taskId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND));

		taskRepository.delete(existing);
	}

}
