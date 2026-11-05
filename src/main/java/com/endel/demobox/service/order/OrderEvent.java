package com.endel.demobox.service.order;

/** Published when an order is created or changes status; listeners react after the transaction commits. */
public record OrderEvent(Long orderId, Type type) {
    public enum Type {
        CREATED,
        PAID,
        DELIVERED,
        CANCELLED
    }
}
