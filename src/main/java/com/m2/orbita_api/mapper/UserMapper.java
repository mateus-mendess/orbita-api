package com.m2.orbita_api.mapper;

import com.m2.orbita_api.model.dto.request.UserRequest;
import com.m2.orbita_api.model.dto.response.UserResponse;
import com.m2.orbita_api.model.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toEntity(UserRequest request);

    UserResponse toResponse(User user);
}
