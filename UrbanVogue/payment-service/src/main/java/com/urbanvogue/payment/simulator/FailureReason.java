package com.urbanvogue.payment.simulator;

/**
 * Reasons why a simulated payment can fail.
 *
 * <p>Each reason has a corresponding weight in application.properties
 * that controls its relative probability of being selected.</p>
 */
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    CARD_EXPIRED,
    BANK_DECLINED,
    FRAUD_SUSPECTED,
    NETWORK_ERROR
}
