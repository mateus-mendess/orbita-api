package com.orbita.api.task.internal.model;

import com.orbita.api.common.model.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "tasks")
@CompoundIndex(name = "user_date_start_idx", def = "{'userId': 1, 'date': 1, 'startTime': 1}")
public class Task {

	@Id
	private String id;

	@Indexed
	private String userId;

	private String title;

	private String description;

	private String date;

	private String startTime;

	private String endTime;

	private Urgency urgency;

	private Category category;

	private boolean completed;

	private Instant createdAt;

	private Instant updatedAt;

}
