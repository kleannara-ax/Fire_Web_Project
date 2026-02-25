package com.company.module.sales.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 영업 주문 상세 라인 엔티티
 * <p>
 * 테이블명: MOD_SALES_ORDER_LINE
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "MOD_SALES_ORDER_LINE")
public class SalesOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Long lineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    /** 라인 번호 (1, 2, 3...) */
    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    /** 품목 코드 */
    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    /** 품목명 */
    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    /** 수량 */
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    /** 단가 */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** 라인 금액 (수량 × 단가) */
    @Column(name = "line_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineAmount;

    /** 비고 */
    @Column(name = "note", length = 500)
    private String note;

    @Builder
    public SalesOrderLine(SalesOrder order, int lineNumber,
                          String itemCode, String itemName,
                          BigDecimal quantity, BigDecimal unitPrice, String note) {
        this.order = order;
        this.lineNumber = lineNumber;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = quantity.multiply(unitPrice);
        this.note = note;
    }

    public void update(String itemCode, String itemName,
                       BigDecimal quantity, BigDecimal unitPrice, String note) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = quantity.multiply(unitPrice);
        this.note = note;
    }
}
