package com.ecommerce.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OrderStatusTransitionException extends RuntimeException {

    public OrderStatusTransitionException(String message) {
        super(message);
    }
}
