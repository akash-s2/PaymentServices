package com.urbanvogue.payment.simulator;

/**
 * Immutable result of a payment simulation.
 *
 * @param success       whether the simulated payment succeeded
 * @param failureReason the reason for failure (null when success is true)
 */
public record PaymentSimulationResult(
        boolean success,
        FailureReason failureReason
) {

    public static PaymentSimulationResult ofSuccess() {
        return new PaymentSimulationResult(true, null);
    }

    public static PaymentSimulationResult ofFailure(FailureReason reason) {
        return new PaymentSimulationResult(false, reason);
    }
}
