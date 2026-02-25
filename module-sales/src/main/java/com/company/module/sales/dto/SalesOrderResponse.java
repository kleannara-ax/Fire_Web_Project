package com.company.module.sales.dto;

import com.company.module.sales.entity.OrderStatus;
import com.company.module.sales.entity.SalesOrder;
import com.company.module.sales.entity.SalesOrderLine;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 영업 주문 응답 DTO
 */
@Getter
public class SalesOrderResponse {

    private final Long orderId;
    private final String orderNumber;
    private final String customerCode;
    private final String customerName;
    private final LocalDate orderDate;
    private final LocalDate deliveryDate;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String note;
    private final String createdByName;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // 주문 라인 (상세 조회 시)
    private List<OrderLineDto> orderLines;

    public SalesOrderResponse(SalesOrder o) {
        this.orderId = o.getOrderId();
        this.orderNumber = o.getOrderNumber();
        this.customerCode = o.getCustomerCode();
        this.customerName = o.getCustomerName();
        this.orderDate = o.getOrderDate();
        this.deliveryDate = o.getDeliveryDate();
        this.status = o.getStatus();
        this.totalAmount = o.getTotalAmount();
        this.note = o.getNote();
        this.createdByName = o.getCreatedByName();
        this.createdAt = o.getCreatedAt();
        this.updatedAt = o.getUpdatedAt();
    }

    public void setOrderLines(List<SalesOrderLine> lines) {
        this.orderLines = lines.stream()
                .map(OrderLineDto::new)
                .collect(Collectors.toList());
    }

    @Getter
    public static class OrderLineDto {
        private final Long lineId;
        private final int lineNumber;
        private final String itemCode;
        private final String itemName;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal lineAmount;
        private final String note;

        public OrderLineDto(SalesOrderLine l) {
            this.lineId = l.getLineId();
            this.lineNumber = l.getLineNumber();
            this.itemCode = l.getItemCode();
            this.itemName = l.getItemName();
            this.quantity = l.getQuantity();
            this.unitPrice = l.getUnitPrice();
            this.lineAmount = l.getLineAmount();
            this.note = l.getNote();
        }
    }
}
