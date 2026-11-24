package com.orbita.api.note.internal.repository;

import com.orbita.api.common.model.Category;
import com.orbita.api.note.internal.model.Note;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends MongoRepository<Note, String> {

	List<Note> findAllByUserIdOrderByCreatedAtDesc(String userId);

	List<Note> findAllByUserIdAndCategoryOrderByCreatedAtDesc(String userId, Category category);

	Optional<Note> findByIdAndUserId(String id, String userId);

}
