package com.endel.demobox.model.dto.order;

import com.endel.demobox.model.order.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDto {
    private Long id;
    private String folio;
    private OrderStatus status;
    private String statusLabel;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String street;
    private String neighborhood;
    private String postalCode;
    private String addressReferences;
    private String city;
    private String notes;
    private BigDecimal totalMxn;
    private List<OrderItemDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private BigDecimal paidAmountMxn;
    private String paymentNote;
    private String paidBy;
    private LocalDateTime deliveredAt;
    private String deliveredBy;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String cancelReason;

    public String getFullAddress() {
        return street + ", Col. " + neighborhood + ", C.P. " + postalCode + ", " + city;
    }
}
