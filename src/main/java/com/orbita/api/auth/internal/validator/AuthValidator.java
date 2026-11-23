package com.orbita.api.auth.internal.validator;

import com.orbita.api.common.exception.ErrorCode;
import com.orbita.api.common.exception.FieldValidationException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuthValidator {

	public void validatePasswordsMatch(String password, String confirmPassword) {
		if (!Objects.equals(password, confirmPassword)) {
			throw new FieldValidationException("confirmPassword", ErrorCode.PASSWORDS_DO_NOT_MATCH);
		}
	}

}
