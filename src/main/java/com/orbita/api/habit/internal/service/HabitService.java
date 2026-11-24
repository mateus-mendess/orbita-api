package com.orbita.api.habit.internal.service;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.OrbitaApiException;
import com.orbita.api.habit.internal.dto.HabitCompletionRequest;
import com.orbita.api.habit.internal.dto.HabitRequest;
import com.orbita.api.habit.internal.dto.HabitResponse;
import com.orbita.api.habit.internal.mapper.HabitMapper;
import com.orbita.api.habit.internal.model.Habit;
import com.orbita.api.habit.internal.repository.HabitRepository;
import com.orbita.api.habit.internal.validator.HabitValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

	public static final String HABITS_CACHE = "habits";
	private static final int MAX_HABITS_PER_USER = 50;

	private final HabitRepository habitRepository;
	private final HabitValidator habitValidator;
	private final HabitMapper habitMapper;

	@Cacheable(value = HABITS_CACHE, key = "#userId")
	public List<HabitResponse> listAll(String userId) {
		return habitRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
				.stream()
				.map(habitMapper::toResponse)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	@CacheEvict(value = HABITS_CACHE, key = "#userId")
	public HabitResponse create(String userId, HabitRequest request) {
		habitValidator.validateHabit(request.title(), request.daysOfWeek());

		if (habitRepository.countByUserId(userId) >= MAX_HABITS_PER_USER) {
			throw new OrbitaApiException(ErrorCode.HABIT_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY);
		}

		Instant now = Instant.now();
		Habit habit = habitMapper.toEntity(request);
		habit.setUserId(userId);
		habit.setTitle(request.title().trim());
		habit.setDaysOfWeek(sortedDays(request.daysOfWeek()));
		habit.setCompletedDates(new ArrayList<>());
		habit.setCreatedAt(now);
		habit.setUpdatedAt(now);

		Habit saved = habitRepository.save(habit);
		return habitMapper.toResponse(saved);
	}

	@CacheEvict(value = HABITS_CACHE, key = "#userId")
	public HabitResponse update(String userId, String habitId, HabitRequest request) {
		habitValidator.validateHabit(request.title(), request.daysOfWeek());

		Habit existing = habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.HABIT_NOT_FOUND, HttpStatus.NOT_FOUND));

		existing.setTitle(request.title().trim());
		existing.setIcon(request.icon());
		existing.setDaysOfWeek(sortedDays(request.daysOfWeek()));
		existing.setUpdatedAt(Instant.now());

		Habit saved = habitRepository.save(existing);
		return habitMapper.toResponse(saved);
	}

	@CacheEvict(value = HABITS_CACHE, key = "#userId")
	public HabitResponse updateCompletion(String userId, String habitId, HabitCompletionRequest request) {
		habitValidator.validateCompletionDate(request.date());

		Habit existing = habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.HABIT_NOT_FOUND, HttpStatus.NOT_FOUND));

		List<String> completedDates = existing.getCompletedDates() != null
				? new ArrayList<>(existing.getCompletedDates())
				: new ArrayList<>();

		if (Boolean.TRUE.equals(request.completed())) {
			if (!completedDates.contains(request.date())) {
				completedDates.add(request.date());
				Collections.sort(completedDates);
			}
		} else {
			completedDates.remove(request.date());
		}

		existing.setCompletedDates(completedDates);
		existing.setUpdatedAt(Instant.now());

		Habit saved = habitRepository.save(existing);
		return habitMapper.toResponse(saved);
	}

	@CacheEvict(value = HABITS_CACHE, key = "#userId")
	public void delete(String userId, String habitId) {
		Habit existing = habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(() -> new OrbitaApiException(ErrorCode.HABIT_NOT_FOUND, HttpStatus.NOT_FOUND));

		habitRepository.delete(existing);
	}

	private List<Integer> sortedDays(List<Integer> daysOfWeek) {
		List<Integer> copy = new ArrayList<>(daysOfWeek);
		Collections.sort(copy);
		return copy;
	}

}
