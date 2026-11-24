package com.orbita.api.habit.internal.mapper;

import com.orbita.api.habit.internal.dto.HabitRequest;
import com.orbita.api.habit.internal.dto.HabitResponse;
import com.orbita.api.habit.internal.model.Habit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HabitMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "userId", ignore = true)
	@Mapping(target = "completedDates", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Habit toEntity(HabitRequest request);

	@Mapping(target = "frequencyDaysPerWeek", expression = "java(habit.getDaysOfWeek() != null ? habit.getDaysOfWeek().size() : 0)")
	HabitResponse toResponse(Habit habit);

}
