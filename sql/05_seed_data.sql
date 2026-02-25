-- =============================================================================
-- 05_seed_data.sql
-- 초기 마스터 데이터 + 관리자 계정 생성
-- =============================================================================

USE fireweb;

-- -----------------------------------------------------------------------
-- 초기 관리자 계정 (BCrypt hash of 'Admin123!')
-- BCrypt hash는 Spring Boot 실행 후 갱신 필요
-- 임시 해시: $2a$10$이하의 값은 실제 BCrypt 인코더로 생성해야 함
-- -----------------------------------------------------------------------
INSERT INTO web_user (username, display_name, password_hash, role, is_active)
VALUES ('admin', '시스템 관리자', '$2a$10$placeholder_change_before_use', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE username = username;

-- -----------------------------------------------------------------------
-- 기본 건물 데이터
-- -----------------------------------------------------------------------
INSERT INTO building (building_name, is_active) VALUES
    ('복지관', 1),
    ('관리동', 1)
ON DUPLICATE KEY UPDATE building_name = building_name;

-- -----------------------------------------------------------------------
-- 기본 층 데이터
-- -----------------------------------------------------------------------
INSERT INTO floor (floor_name, sort_order) VALUES
    ('지하 1층', 1),
    ('1층',     2),
    ('2층',     3),
    ('3층',     4)
ON DUPLICATE KEY UPDATE floor_name = floor_name;

-- -----------------------------------------------------------------------
-- NOTE: 관리자 계정 초기 비밀번호 설정 방법
--
-- 애플리케이션 실행 후 아래 API를 호출하여 관리자 계정 비밀번호 재설정:
--
-- 1. /api/auth/login API로 임시 로그인 (별도 초기화 엔드포인트 필요)
-- 2. 또는 직접 BCrypt 해시 생성 후 UPDATE:
--
-- UPDATE web_user
-- SET password_hash = '[BCrypt 해시 문자열]'
-- WHERE username = 'admin';
--
-- BCrypt 해시 생성 예시 (Java):
-- BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
-- String hash = encoder.encode("Admin123!");
-- -----------------------------------------------------------------------
