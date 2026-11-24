package com.orbita.api.task.internal.mapper;

import com.orbita.api.task.internal.dto.TaskRequest;
import com.orbita.api.task.internal.dto.TaskResponse;
import com.orbita.api.task.internal.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "userId", ignore = true)
	@Mapping(target = "completed", constant = "false")
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Task toEntity(TaskRequest request);

	TaskResponse toResponse(Task task);

}
