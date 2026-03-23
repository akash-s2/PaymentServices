package com.urbanvogue.payment.exception;

/**
 * Thrown when a payment request is invalid (e.g., order not in CREATED state,
 * amount <= 0, duplicate SUCCESS payment already exists).
 */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
