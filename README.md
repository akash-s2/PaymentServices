# PaymentServices

***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 1 of constructor in com.urbanvogue.payment.simulator.PaymentSimulator required a single bean, but 2 were found:
	- paymentSimulatorProperties: defined in file [C:\Users\akash.s2\Documents\UrbanVogue-AG\payment-service\target\classes\com\urbanvogue\payment\simulator\PaymentSimulatorProperties.class]
	- payment.simulator-com.urbanvogue.payment.simulator.PaymentSimulatorProperties: defined in unknown location

This may be due to missing parameter name information

Action:

Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed

Ensure that your compiler is configured to use the '-parameters' flag.
You may need to update both your build tool settings as well as your IDE.
(See https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x#parameter-name-retention)


Disconnected from the target VM, address: '127.0.0.1:49827', transport: 'socket'

package com.urbanvogue.payment.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code payment.simulator.failure-reasons.*} properties
 * into a typed Map for use by {@link PaymentSimulator}.
 *
 * <p>Registered as a bean via {@code @EnableConfigurationProperties}
 * in {@link com.urbanvogue.payment.PaymentServiceApplication}.</p>
 */
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
