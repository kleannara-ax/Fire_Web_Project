-- =============================================================
-- FireWeb - MariaDB DDL 스크립트
-- Spring Boot 3.x + JPA (ddl-auto: none)
-- 직접 실행하여 스키마를 구성해야 합니다.
-- =============================================================

-- 데이터베이스 생성 (없는 경우)
CREATE DATABASE IF NOT EXISTS fireweb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fireweb;

-- =============================================================
-- 공통 마스터 테이블 (module-fire)
-- =============================================================

-- 건물 마스터
CREATE TABLE IF NOT EXISTS building (
    building_id   BIGINT       NOT NULL AUTO_INCREMENT,
    building_name VARCHAR(200) NOT NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 층 마스터
CREATE TABLE IF NOT EXISTS floor (
    floor_id   BIGINT       NOT NULL AUTO_INCREMENT,
    floor_name VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (floor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 구역(Zone) 마스터
CREATE TABLE IF NOT EXISTS zone (
    zone_id     BIGINT        NOT NULL AUTO_INCREMENT,
    zone_code   VARCHAR(50)   NOT NULL,
    zone_name   VARCHAR(200),
    building_id BIGINT,
    floor_id    BIGINT,
    x           DECIMAL(9,4),
    y           DECIMAL(9,4),
    PRIMARY KEY (zone_id),
    CONSTRAINT fk_zone_building FOREIGN KEY (building_id) REFERENCES building (building_id) ON DELETE SET NULL,
    CONSTRAINT fk_zone_floor    FOREIGN KEY (floor_id)    REFERENCES floor    (floor_id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 사용자/권한 (module-user)
-- =============================================================

CREATE TABLE IF NOT EXISTS web_user (
    user_id       BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_web_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 소화기 도메인 (module-fire)
-- =============================================================

-- 소화기 위치 그룹 (도면 마커 단위)
CREATE TABLE IF NOT EXISTS extinguisher_group (
    group_id    BIGINT        NOT NULL AUTO_INCREMENT,
    building_id BIGINT        NOT NULL,
    floor_id    BIGINT        NOT NULL,
    x           DECIMAL(9,4),
    y           DECIMAL(9,4),
    note        VARCHAR(400),
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id),
    CONSTRAINT fk_extgrp_building FOREIGN KEY (building_id) REFERENCES building (building_id) ON DELETE RESTRICT,
    CONSTRAINT fk_extgrp_floor    FOREIGN KEY (floor_id)    REFERENCES floor    (floor_id)    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소화기
CREATE TABLE IF NOT EXISTS extinguisher (
    extinguisher_id       BIGINT        NOT NULL AUTO_INCREMENT,
    serial_number         VARCHAR(50)   NOT NULL COMMENT 'EXT-000001 형식',
    building_id           BIGINT        NOT NULL,
    floor_id              BIGINT        NOT NULL,
    group_id              BIGINT        COMMENT '도면 위치 그룹 (NULL 허용)',
    extinguisher_type     VARCHAR(100)  NOT NULL COMMENT '분말, CO2 등',
    install_date          DATE          NOT NULL COMMENT '제조일',
    replacement_cycle_years INT         NOT NULL DEFAULT 5,
    replacement_due_date  DATE          COMMENT '교체 예정일 (서비스에서 계산)',
    quantity              INT           NOT NULL DEFAULT 1,
    x                     DECIMAL(9,4),
    y                     DECIMAL(9,4),
    image_path            VARCHAR(600),
    note                  VARCHAR(500),
    note_key              VARCHAR(100)  NOT NULL COMMENT 'QR 조회용 고정 키 (UUID)',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (extinguisher_id),
    UNIQUE KEY uk_extinguisher_serial  (serial_number),
    UNIQUE KEY uk_extinguisher_notekey (note_key),
    CONSTRAINT fk_ext_building FOREIGN KEY (building_id) REFERENCES building           (building_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ext_floor    FOREIGN KEY (floor_id)    REFERENCES floor              (floor_id)    ON DELETE RESTRICT,
    CONSTRAINT fk_ext_group    FOREIGN KEY (group_id)    REFERENCES extinguisher_group (group_id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소화기 점검 이력
CREATE TABLE IF NOT EXISTS extinguisher_inspection (
    inspection_id         BIGINT       NOT NULL AUTO_INCREMENT,
    extinguisher_id       BIGINT       NOT NULL,
    inspection_date       DATE         NOT NULL,
    is_faulty             TINYINT(1)   NOT NULL DEFAULT 0,
    fault_reason          VARCHAR(500),
    inspected_by_user_id  BIGINT       COMMENT 'WebUser FK (참조 무결성 미적용 - 사용자 삭제 대응)',
    inspected_by_name     VARCHAR(200) COMMENT '점검자 표시 이름 스냅샷',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_ext_inspection_date (extinguisher_id, inspection_date),
    CONSTRAINT fk_extinsp_ext FOREIGN KEY (extinguisher_id) REFERENCES extinguisher (extinguisher_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_extinsp_extid_date
    ON extinguisher_inspection (extinguisher_id, inspection_date DESC);

-- =============================================================
-- 소화전 도메인 (module-fire)
-- =============================================================

-- 소화전
CREATE TABLE IF NOT EXISTS fire_hydrant (
    hydrant_id           BIGINT       NOT NULL AUTO_INCREMENT,
    serial_number        VARCHAR(50)  NOT NULL COMMENT 'HYD-000001 형식',
    hydrant_type         VARCHAR(20)  NOT NULL COMMENT 'Indoor / Outdoor',
    operation_type       VARCHAR(20)  NOT NULL COMMENT 'Auto / Manual',
    building_id          BIGINT       COMMENT '옥외인 경우 id=99',
    floor_id             BIGINT       COMMENT '옥외인 경우 id=1',
    x                    DECIMAL(5,2),
    y                    DECIMAL(5,2),
    location_description VARCHAR(200) COMMENT '옥외 소화전 위치 설명',
    image_path           VARCHAR(600),
    is_active            TINYINT(1)   NOT NULL DEFAULT 1,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hydrant_id),
    UNIQUE KEY uk_hydrant_serial (serial_number),
    CONSTRAINT fk_hyd_building FOREIGN KEY (building_id) REFERENCES building (building_id) ON DELETE RESTRICT,
    CONSTRAINT fk_hyd_floor    FOREIGN KEY (floor_id)    REFERENCES floor    (floor_id)    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소화전 점검 이력
CREATE TABLE IF NOT EXISTS fire_hydrant_inspection (
    inspection_id        BIGINT       NOT NULL AUTO_INCREMENT,
    hydrant_id           BIGINT       NOT NULL,
    inspection_date      DATE         NOT NULL,
    is_faulty            TINYINT(1)   NOT NULL DEFAULT 0,
    fault_reason         VARCHAR(500),
    inspected_by_user_id BIGINT       COMMENT 'WebUser FK (참조 무결성 미적용 - 사용자 삭제 대응)',
    inspected_by_name    VARCHAR(200) COMMENT '점검자 표시 이름 스냅샷',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (inspection_id),
    CONSTRAINT fk_hydinsp_hyd FOREIGN KEY (hydrant_id) REFERENCES fire_hydrant (hydrant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_hydinsp_hydid_date
    ON fire_hydrant_inspection (hydrant_id, inspection_date DESC);

-- =============================================================
-- 영업 도메인 (module-sales)
-- DB 테이블 Prefix: MOD_SALES_
-- =============================================================

-- 영업 주문
CREATE TABLE IF NOT EXISTS MOD_SALES_ORDER (
    order_id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_number      VARCHAR(50)   NOT NULL COMMENT 'ORD-000001 형식',
    customer_code     VARCHAR(50)   NOT NULL COMMENT '거래처 코드',
    customer_name     VARCHAR(200)  NOT NULL COMMENT '거래처명',
    order_date        DATE          NOT NULL COMMENT '주문일',
    delivery_date     DATE          COMMENT '납기일',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                          COMMENT 'DRAFT / CONFIRMED / SHIPPED / COMPLETED / CANCELLED',
    total_amount      DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '총 금액',
    note              VARCHAR(1000),
    created_by_user_id BIGINT       COMMENT 'WebUser FK',
    created_by_name   VARCHAR(200)  COMMENT '등록자 표시 이름 스냅샷',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_sales_order_number (order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_sales_order_date   ON MOD_SALES_ORDER (order_date DESC);
CREATE INDEX IF NOT EXISTS idx_sales_order_status ON MOD_SALES_ORDER (status);
CREATE INDEX IF NOT EXISTS idx_sales_order_cust   ON MOD_SALES_ORDER (customer_code);

-- 영업 주문 상세 라인
CREATE TABLE IF NOT EXISTS MOD_SALES_ORDER_LINE (
    line_id     BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    line_number INT           NOT NULL COMMENT '라인 번호 (1, 2, 3...)',
    item_code   VARCHAR(50)   NOT NULL COMMENT '품목 코드',
    item_name   VARCHAR(200)  NOT NULL COMMENT '품목명',
    quantity    DECIMAL(10,2) NOT NULL COMMENT '수량',
    unit_price  DECIMAL(15,2) NOT NULL COMMENT '단가',
    line_amount DECIMAL(15,2) NOT NULL COMMENT '라인 금액 (수량 × 단가)',
    note        VARCHAR(500),
    PRIMARY KEY (line_id),
    CONSTRAINT fk_sales_line_order FOREIGN KEY (order_id) REFERENCES MOD_SALES_ORDER (order_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_sales_line_order ON MOD_SALES_ORDER_LINE (order_id, line_number);

-- =============================================================
-- 기초 데이터 (샘플)
-- =============================================================

-- 건물 기초 데이터
INSERT IGNORE INTO building (building_id, building_name, is_active) VALUES
    (1,  '복지관',  1),
    (2,  '관리동',  1),
    (99, '옥외',    1);

-- 층 기초 데이터
INSERT IGNORE INTO floor (floor_id, floor_name, sort_order) VALUES
    (1, 'B1',  10),
    (2, '1F',  20),
    (3, '2F',  30),
    (4, '3F',  40),
    (5, '옥상', 50);

-- 관리자 계정 (BCrypt: admin1234! → 실제 배포 시 변경 필수)
-- BCrypt hash of 'admin1234!' with strength 10
INSERT IGNORE INTO web_user (username, display_name, password_hash, role, is_active)
VALUES ('admin', '시스템 관리자',
        '$2a$10$Zr1F9jy9WKVRUq6rmG1LzOejuK.umuuQrM5h1W9CUbm2d1wN2hlGm',
        'ADMIN', 1);
