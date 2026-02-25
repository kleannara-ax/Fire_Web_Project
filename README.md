# FireWeb - Spring Boot 3.x Gradle Multi-Module Project

> ASP.NET Core 8.0 Razor Pages → **Spring Boot 3.2.5 + Java 17 + MariaDB** 전환 프로젝트

---

## 프로젝트 구조

```
fireweb/
├── core/                          # 메인 애플리케이션 모듈 (Spring Boot 진입점)
│   ├── src/main/java/com/company/core/
│   │   ├── FireWebApplication.java          # @SpringBootApplication
│   │   ├── common/ApiResponse.java          # 공통 API 응답 래퍼
│   │   ├── config/
│   │   │   ├── WebMvcConfig.java            # CORS 설정
│   │   │   └── FileUploadProperties.java    # 업로드 경로 설정
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java  # 전역 예외 처리 (@RestControllerAdvice)
│   │   │   ├── BusinessException.java       # 비즈니스 예외 (400)
│   │   │   └── ResourceNotFoundException.java  # 리소스 없음 (404)
│   │   └── security/
│   │       ├── SecurityConfig.java          # Spring Security 설정 (JWT Stateless)
│   │       ├── JwtTokenProvider.java        # JWT 생성/검증
│   │       ├── JwtAuthenticationFilter.java # JWT 인증 필터
│   │       ├── JwtAuthenticationEntryPoint.java  # 401 JSON 응답
│   │       └── JwtProperties.java           # JWT 설정 바인딩
│   └── src/main/resources/
│       └── application.yml                  # MariaDB, JPA, 로그 설정
│
├── module-user/                   # 사용자/인증 모듈
│   └── src/main/java/com/company/module/user/
│       ├── controller/UserController.java   # /api/auth/**, /api/admin/users/**
│       ├── service/UserService.java         # @Transactional (Service only)
│       ├── repository/WebUserRepository.java
│       ├── entity/WebUser.java              # web_user 테이블
│       └── dto/                             # LoginRequest/Response, UserCreateRequest 등
│
├── module-fire/                   # 소화기/소화전 자산 관리 모듈
│   └── src/main/java/com/company/module/fire/
│       ├── controller/
│       │   ├── ExtinguisherController.java  # /fire-api/extinguishers/**
│       │   └── FireHydrantController.java   # /fire-api/hydrants/**
│       ├── service/
│       │   ├── ExtinguisherService.java     # @Transactional (Service only)
│       │   └── FireHydrantService.java
│       ├── repository/                      # JpaRepository (countQuery 분리 적용)
│       ├── entity/                          # Building, Floor, Zone, Extinguisher 등
│       └── dto/                             # ExtinguisherResponse, FireHydrantResponse 등
│
├── module-sales/                  # 영업/매출 관리 모듈 (신규)
│   └── src/main/java/com/company/module/sales/
│       ├── controller/SalesOrderController.java  # /sales-api/orders/**
│       ├── service/SalesOrderService.java         # @Transactional (Service only)
│       ├── repository/                            # countQuery 분리 적용
│       ├── entity/                                # SalesOrder (MOD_SALES_ORDER), SalesOrderLine
│       └── dto/
│
├── sql/
│   ├── 01_schema.sql              # 전체 통합 DDL (권장 사용)
│   ├── 02_ddl_core.sql            # core (web_user) DDL
│   ├── 03_ddl_fire.sql            # fire 도메인 DDL
│   ├── 04_ddl_sales.sql           # sales 도메인 DDL (MOD_SALES_*)
│   └── 05_seed_data.sql           # 기초/샘플 데이터
│
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties  # Gradle 8.7
├── gradlew                        # Unix 실행 스크립트
├── gradlew.bat                    # Windows 실행 스크립트
├── settings.gradle                # 멀티 모듈 선언
└── build.gradle                   # 루트 공통 설정 (Java 17, Spring Boot 3.2.5 BOM)
```

---

## API 엔드포인트 요약

### 인증 (`module-user`)
| Method | URL | 설명 | 인가 |
|--------|-----|------|------|
| POST | `/api/auth/login` | 로그인 → JWT 발급 | 공개 |
| POST | `/api/auth/change-password` | 비밀번호 변경 | 인증 |
| GET | `/api/admin/users` | 전체 사용자 목록 | ADMIN |
| POST | `/api/admin/users` | 사용자 등록 | ADMIN |
| DELETE | `/api/admin/users/{id}` | 사용자 비활성화 | ADMIN |

### 소화기 (`module-fire`)
| Method | URL | 설명 | 인가 |
|--------|-----|------|------|
| GET | `/fire-api/extinguishers` | 목록 조회 (페이징/검색) | 인증 |
| GET | `/fire-api/extinguishers/{id}` | 상세 조회 (점검이력 포함) | 인증 |
| POST | `/fire-api/extinguishers` | 등록/수정 | ADMIN |
| POST | `/fire-api/extinguishers/inspect` | 점검 등록 | 인증 |
| DELETE | `/fire-api/extinguishers/{id}` | 삭제 | ADMIN |

### 소화전 (`module-fire`)
| Method | URL | 설명 | 인가 |
|--------|-----|------|------|
| GET | `/fire-api/hydrants` | 목록 조회 (페이징/검색) | 인증 |
| GET | `/fire-api/hydrants/{id}` | 상세 조회 (점검이력 포함) | 인증 |
| POST | `/fire-api/hydrants` | 등록/수정 | ADMIN |
| POST | `/fire-api/hydrants/{id}/inspect` | 점검 등록 | 인증 |
| DELETE | `/fire-api/hydrants/{id}` | 삭제 | ADMIN |

### 영업 주문 (`module-sales`)
| Method | URL | 설명 | 인가 |
|--------|-----|------|------|
| GET | `/sales-api/orders` | 목록 조회 (페이징/검색/필터) | 인증 |
| GET | `/sales-api/orders/{id}` | 상세 조회 (주문 라인 포함) | 인증 |
| POST | `/sales-api/orders` | 등록/수정 | 인증 |
| POST | `/sales-api/orders/{id}/confirm` | 주문 확정 (DRAFT→CONFIRMED) | 인증 |
| POST | `/sales-api/orders/{id}/cancel` | 주문 취소 | 인증 |
| DELETE | `/sales-api/orders/{id}` | 주문 삭제 (DRAFT만) | ADMIN |

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Build | Gradle 8.7 (Multi-Module) |
| DB | MariaDB (JPA `ddl-auto: none`, 수동 DDL) |
| 인증 | JWT (JJWT 0.12.6) Bearer Token |
| 비밀번호 | BCrypt (Spring Security) |
| ORM | Spring Data JPA + Hibernate |
| 검증 | Jakarta Validation (`@Valid`) |

---

## 데이터베이스 테이블

| 테이블 | 모듈 | 설명 |
|--------|------|------|
| `web_user` | core/user | 사용자 계정 (BCrypt) |
| `building` | fire | 건물 마스터 |
| `floor` | fire | 층 마스터 |
| `zone` | fire | 구역 마스터 |
| `extinguisher_group` | fire | 소화기 위치 그룹 |
| `extinguisher` | fire | 소화기 자산 |
| `extinguisher_inspection` | fire | 소화기 점검 이력 |
| `fire_hydrant` | fire | 소화전 자산 |
| `fire_hydrant_inspection` | fire | 소화전 점검 이력 |
| `MOD_SALES_ORDER` | sales | 영업 주문 |
| `MOD_SALES_ORDER_LINE` | sales | 영업 주문 상세 라인 |

---

## 빠른 시작

### 1. DB 스키마 적용
```bash
mysql -u root -p < sql/01_schema.sql
```

### 2. 설정 수정
`core/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/fireweb?...
    username: fireweb
    password: fireweb1234
security:
  jwt:
    secret: ${JWT_SECRET:your-secret-key}
```

### 3. 빌드 및 실행
```bash
./gradlew :core:bootRun
# 또는
./gradlew :core:bootJar
java -jar core/build/libs/fireweb-1.0.0.jar
```

---

## 수정 이력

| 버전 | 내용 |
|------|------|
| v1.1.0 | **버그 수정**: boolean 필드명 충돌(`isActive`→`active`) 수정, JPQL countQuery 분리, gradlew 추가 |
| v1.0.0 | ASP.NET Core → Spring Boot 3.x 최초 전환 (core + module-user + module-fire + module-sales) |

---

## GitHub
- **Repository**: https://github.com/kleannara-ax/Fire_Web_Project
