package com.company.module.sales.entity;

/**
 * 주문 상태 Enum
 */
public enum OrderStatus {
    /** 임시저장 */
    DRAFT,
    /** 확정 */
    CONFIRMED,
    /** 출하 */
    SHIPPED,
    /** 완료 */
    COMPLETED,
    /** 취소 */
    CANCELLED
}
