package com.orbita.api.habit.internal.repository;

import com.orbita.api.habit.internal.model.Habit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends MongoRepository<Habit, String> {

	List<Habit> findAllByUserIdOrderByCreatedAtDesc(String userId);

	Optional<Habit> findByIdAndUserId(String id, String userId);

	long countByUserId(String userId);

}
