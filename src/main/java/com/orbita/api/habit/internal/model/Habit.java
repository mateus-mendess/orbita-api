package com.orbita.api.habit.internal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "habits")
@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
public class Habit {

	@Id
	private String id;

	private String userId;

	private String title;

	private String icon;

	@Builder.Default
	private List<Integer> daysOfWeek = new ArrayList<>();

	@Builder.Default
	private List<String> completedDates = new ArrayList<>();

	private Instant createdAt;

	private Instant updatedAt;

}
