package com.endel.demobox.model.order;

public enum OrderStatus {
    PENDING("Pendiente"),
    PAID("Pagado"),
    DELIVERED("Entregado"),
    CANCELLED("Cancelado");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
