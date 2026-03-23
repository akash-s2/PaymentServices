package com.urbanvogue.payment.simulator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Simulates payment gateway behaviour with configurable outcomes.
 *
 * <p>Success probability is controlled via {@code payment.simulator.success-rate}.
 * When a payment fails, the specific failure reason is chosen using weighted
 * random selection from {@code payment.simulator.failure-reasons.*}.</p>
 */
@Component
public class PaymentSimulator {

    private final double successRate;
    private final Map<String, Integer> failureReasonWeights;
    private final Random random;

    public PaymentSimulator(
            @Value("${payment.simulator.success-rate:0.8}") double successRate,
            PaymentSimulatorProperties properties) {
        this.successRate = successRate;
        this.failureReasonWeights = properties.getFailureReasons();
        this.random = new Random();
    }

    /**
     * Simulates a payment attempt.
     *
     * @return a {@link PaymentSimulationResult} indicating success or failure with reason
     */
    public PaymentSimulationResult simulate() {
        if (random.nextDouble() < successRate) {
            return PaymentSimulationResult.ofSuccess();
        }
        return PaymentSimulationResult.ofFailure(pickFailureReason());
    }

    /**
     * Picks a failure reason using weighted random selection.
     * Weights are read from {@code payment.simulator.failure-reasons.*} properties.
     */
    private FailureReason pickFailureReason() {
        if (failureReasonWeights == null || failureReasonWeights.isEmpty()) {
            return FailureReason.BANK_DECLINED; // safe default
        }

        int totalWeight = failureReasonWeights.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<String, Integer> entry : failureReasonWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return FailureReason.valueOf(entry.getKey());
            }
        }

        // Should never reach here, but safe fallback
        return FailureReason.BANK_DECLINED;
    }
}
