package com.orbita.api.auth.internal.mapper;

import com.orbita.api.auth.internal.dto.RegisterResponse;
import com.orbita.api.auth.internal.dto.UserMeResponse;
import com.orbita.api.auth.internal.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

	RegisterResponse toRegisterResponse(User user);

	UserMeResponse toUserMeResponse(User user);

}
