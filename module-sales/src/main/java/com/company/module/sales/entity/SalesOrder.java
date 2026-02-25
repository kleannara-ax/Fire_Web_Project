package com.company.module.sales.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 영업 주문 엔티티 (module-sales 샘플)
 * <p>
 * - 패키지: com.company.module.sales
 * - URL Prefix: /sales-api/**
 * - DB 테이블 Prefix: MOD_SALES_
 * - Core 보안/예외 처리 구조 그대로 활용
 * - Core 소스 수정 없음
 *
 * 테이블명: MOD_SALES_ORDER
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "MOD_SALES_ORDER")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    /** 주문 번호 (ORD-000001 형식) */
    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    /** 거래처 코드 */
    @Column(name = "customer_code", nullable = false, length = 50)
    private String customerCode;

    /** 거래처명 */
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    /** 주문일 */
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** 납기일 */
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /**
     * 주문 상태
     * DRAFT(임시저장), CONFIRMED(확정), SHIPPED(출하), COMPLETED(완료), CANCELLED(취소)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.DRAFT;

    /** 총 금액 */
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 비고 */
    @Column(name = "note", length = 1000)
    private String note;

    /** 등록자 ID (WebUser FK) */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /** 등록자명 스냅샷 */
    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 주문 상세 라인 (1:N) */
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<SalesOrderLine> orderLines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public SalesOrder(String orderNumber, String customerCode, String customerName,
                      LocalDate orderDate, LocalDate deliveryDate,
                      String note, Long createdByUserId, String createdByName) {
        this.orderNumber = orderNumber;
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
        this.status = OrderStatus.DRAFT;
        this.totalAmount = BigDecimal.ZERO;
        this.note = note;
        this.createdByUserId = createdByUserId;
        this.createdByName = createdByName;
    }

    // ===== 비즈니스 메서드 =====

    public void confirm() {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("임시저장 상태의 주문만 확정할 수 있습니다.");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void updateInfo(String customerCode, String customerName,
                           LocalDate orderDate, LocalDate deliveryDate, String note) {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("임시저장 상태의 주문만 수정할 수 있습니다.");
        }
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
        this.note = note;
    }

    public void recalculateTotalAmount() {
        this.totalAmount = orderLines.stream()
                .map(SalesOrderLine::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
