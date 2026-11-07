package com.ecommerce.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String message) {
        super(message);
    }
}
