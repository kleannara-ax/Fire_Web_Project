-- =============================================================================
-- 04_ddl_sales.sql
-- module-sales 신규 업무 모듈 DDL (MariaDB)
-- - DB 테이블 Prefix: MOD_SALES_
-- - Core 보안/예외처리 구조와 독립적으로 동작
-- =============================================================================

USE fireweb;

-- -----------------------------------------------------------------------
-- 영업 주문 헤더
-- 테이블명: MOD_SALES_ORDER
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS MOD_SALES_ORDER (
    order_id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
    order_number        VARCHAR(50)     NOT NULL                COMMENT '주문 번호 (ORD-000001)',
    customer_code       VARCHAR(50)     NOT NULL                COMMENT '거래처 코드',
    customer_name       VARCHAR(200)    NOT NULL                COMMENT '거래처명',
    order_date          DATE            NOT NULL                COMMENT '주문일',
    delivery_date       DATE                                    COMMENT '납기일',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '주문 상태 (DRAFT/CONFIRMED/SHIPPED/COMPLETED/CANCELLED)',
    total_amount        DECIMAL(15,2)   NOT NULL DEFAULT 0.00   COMMENT '총 금액',
    note                VARCHAR(1000)                           COMMENT '비고',
    created_by_user_id  BIGINT                                  COMMENT '등록자 ID (web_user FK)',
    created_by_name     VARCHAR(200)                            COMMENT '등록자명 스냅샷',
    created_at          DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',
    updated_at          DATETIME        NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '수정일시',

    PRIMARY KEY (order_id),
    UNIQUE KEY uk_sales_order_number (order_number),
    INDEX idx_sales_order_status     (status),
    INDEX idx_sales_order_date       (order_date),
    INDEX idx_sales_order_customer   (customer_code),
    CONSTRAINT fk_sales_order_user FOREIGN KEY (created_by_user_id) REFERENCES web_user(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='영업 주문';

-- -----------------------------------------------------------------------
-- 영업 주문 상세 라인
-- 테이블명: MOD_SALES_ORDER_LINE
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS MOD_SALES_ORDER_LINE (
    line_id         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '라인 ID',
    order_id        BIGINT          NOT NULL                COMMENT '주문 FK',
    line_number     INT             NOT NULL                COMMENT '라인 번호',
    item_code       VARCHAR(50)     NOT NULL                COMMENT '품목 코드',
    item_name       VARCHAR(200)    NOT NULL                COMMENT '품목명',
    quantity        DECIMAL(10,2)   NOT NULL                COMMENT '수량',
    unit_price      DECIMAL(15,2)   NOT NULL                COMMENT '단가',
    line_amount     DECIMAL(15,2)   NOT NULL                COMMENT '라인 금액',
    note            VARCHAR(500)                            COMMENT '비고',

    PRIMARY KEY (line_id),
    INDEX idx_sales_line_order (order_id),
    CONSTRAINT fk_sales_line_order FOREIGN KEY (order_id) REFERENCES MOD_SALES_ORDER(order_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='영업 주문 상세 라인';
