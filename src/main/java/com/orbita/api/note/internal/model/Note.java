package com.orbita.api.note.internal.model;

import com.orbita.api.common.model.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notes")
@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
@CompoundIndex(name = "user_category_created_idx", def = "{'userId': 1, 'category': 1, 'createdAt': -1}")
public class Note {

	@Id
	private String id;

	private String userId;

	private String title;

	private Category category;

	private String content;

	private Instant createdAt;

	private Instant updatedAt;

}
