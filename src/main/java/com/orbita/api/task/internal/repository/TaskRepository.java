package com.orbita.api.task.internal.repository;

import com.orbita.api.task.internal.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends MongoRepository<Task, String> {

	@Query(value = "{ 'userId': ?0, 'date': { $gte: ?1, $lte: ?2 } }", sort = "{ 'date': 1, 'startTime': 1 }")
	List<Task> findAllByUserIdAndDateRange(String userId, String from, String to);

	Optional<Task> findByIdAndUserId(String id, String userId);

}
