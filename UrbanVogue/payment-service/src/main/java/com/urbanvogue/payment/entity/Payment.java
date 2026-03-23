package com.urbanvogue.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment entity — the core aggregate of this microservice.
 *
 * <p>All state mutations happen through controlled methods only.
 * There are NO public setters, ensuring invariants are always upheld.</p>
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column
    private String transactionId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private boolean orderUpdated;

    @Column
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ----- JPA requires a no-arg constructor (protected to prevent misuse) -----
    protected Payment() {
    }

    // ----- The only way to create a Payment: fully specified constructor -----
    public Payment(Long orderId,
                   BigDecimal amount,
                   PaymentMethod method,
                   String idempotencyKey) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.INITIATED;
        this.orderUpdated = false;
    }

    // ----- Lifecycle callback -----

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ----- Controlled mutation methods -----

    /**
     * Marks the payment as successful and records the transaction ID
     * issued by the payment gateway.
     */
    public void markSuccess(String transactionId) {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException(
                    "Cannot mark payment as SUCCESS — current status: " + this.status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.transactionId = transactionId;
    }

    /**
     * Marks the payment as failed with a specific reason
     * (e.g., INSUFFICIENT_FUNDS, CARD_EXPIRED).
     */
    public void markFailed(String failureReason) {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException(
                    "Cannot mark payment as FAILED — current status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    /**
     * Marks that the Order Service has been successfully notified
     * about this payment's outcome.
     */
    public void markOrderUpdated() {
        this.orderUpdated = true;
    }

    // ----- Read-only accessors -----

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public boolean isOrderUpdated() {
        return orderUpdated;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
