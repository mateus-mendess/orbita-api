package com.orbita.api.auth.internal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

	@Id
	private String id;

	private String name;

	@Indexed(unique = true)
	private String email;

	private String passwordHash;

	private boolean emailVerified;

	private Instant createdAt;

	private Instant updatedAt;

}
