package com.urbanvogue.payment.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code payment.simulator.failure-reasons.*} properties
 * into a typed Map for use by {@link PaymentSimulator}.
 */
@Component
@ConfigurationProperties(prefix = "payment.simulator")
public class PaymentSimulatorProperties {

    private Map<String, Integer> failureReasons = new HashMap<>();

    public Map<String, Integer> getFailureReasons() {
        return failureReasons;
    }

    public void setFailureReasons(Map<String, Integer> failureReasons) {
        this.failureReasons = failureReasons;
    }
}
