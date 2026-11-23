package com.orbita.api.auth.internal.repository;

import com.orbita.api.auth.internal.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByEmail(String email);

}
