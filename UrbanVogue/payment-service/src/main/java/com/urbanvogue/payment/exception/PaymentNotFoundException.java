package com.urbanvogue.payment.exception;

/**
 * Thrown when a payment cannot be found by the given criteria.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
