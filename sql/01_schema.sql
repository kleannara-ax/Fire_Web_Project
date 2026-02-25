-- =============================================================================
-- FireWeb - MariaDB DDL 스크립트
-- JPA DDL 자동 생성 비활성화 (spring.jpa.hibernate.ddl-auto=none)이므로
-- 이 스크립트를 수동으로 실행하여 스키마를 생성합니다.
--
-- 실행 순서:
-- 1. 01_schema.sql  (DB/계정 생성)
-- 2. 02_ddl_core.sql (기준 마스터 + 사용자)
-- 3. 03_ddl_fire.sql (소화기/소화전 도메인)
-- 4. 04_ddl_sales.sql (module-sales 신규 모듈)
-- 5. 05_seed_data.sql (초기 데이터)
-- =============================================================================

-- 데이터베이스 및 계정 생성 (root 권한 필요)
CREATE DATABASE IF NOT EXISTS fireweb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 전용 계정 생성 (운영 환경에서는 강한 비밀번호 사용)
CREATE USER IF NOT EXISTS 'fireweb'@'localhost' IDENTIFIED BY 'fireweb1234';
CREATE USER IF NOT EXISTS 'fireweb'@'%' IDENTIFIED BY 'fireweb1234';

-- 권한 부여
GRANT ALL PRIVILEGES ON fireweb.* TO 'fireweb'@'localhost';
GRANT ALL PRIVILEGES ON fireweb.* TO 'fireweb'@'%';
FLUSH PRIVILEGES;
