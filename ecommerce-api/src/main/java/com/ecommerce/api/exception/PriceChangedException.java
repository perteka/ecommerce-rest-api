package com.ecommerce.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PriceChangedException extends RuntimeException {

    public PriceChangedException(String message) {
        super(message);
    }
}
