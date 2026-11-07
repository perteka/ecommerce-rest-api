package com.ecommerce.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidProductQueryException extends RuntimeException {

    public InvalidProductQueryException(String message) {
        super(message);
    }
}
