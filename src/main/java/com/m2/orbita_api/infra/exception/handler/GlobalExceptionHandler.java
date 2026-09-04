package com.m2.orbita_api.infra.exception.handler;

import com.m2.orbita_api.infra.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrbitaApiException.class)
    public ProblemDetail handleOrbitaApiException(OrbitaApiException ex){
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExistsException(EmailAlreadyExistsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CodeExpiredException.class)
    public ProblemDetail handleCodeExpiredException(CodeExpiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ProblemDetail handleInvalidCodeException(InvalidCodeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

}
