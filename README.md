# FireWeb - Spring Boot Multi-Module 전환 프로젝트

## 프로젝트 개요
소화기/소화전 자산 관리, 도면 좌표 매핑, 점검 이력 관리, QR 기반 조회/점검 기능을 제공하는 시스템.
기존 ASP.NET Core Razor Pages(.NET 8 + SQL Server) → **Gradle Multi-Module + Spring Boot 3.x + MariaDB**로 전환.

---

## 기술 스택

| 항목 | 기존(ASP.NET) | 변환 후(Spring Boot) |
|------|--------------|---------------------|
| Framework | ASP.NET Core 8 Razor Pages | Spring Boot 3.2.5 |
| Language | C# | Java 17 |
| ORM | Entity Framework Core | Spring Data JPA |
| DB | SQL Server 2019 Express | MariaDB |
| 인증 | Cookie Authentication | JWT Bearer (JJWT 0.12.x) |
| 비밀번호 | PBKDF2-SHA256 | BCrypt |
| 로깅 | Serilog | SLF4J + Logback |
| 빌드 | .NET CLI (.csproj) | Gradle Multi-Module |

---

## 모듈 구조

```
fireweb/                          ← 루트 프로젝트
├── settings.gradle               ← 멀티 모듈 등록
├── build.gradle                  ← 공통 의존성/설정
│
├── core/                         ← 애플리케이션 진입점 + 공통
│   └── src/main/java/com/company/core/
│       ├── FireWebApplication.java      ← @SpringBootApplication
│       ├── common/
│       │   └── ApiResponse.java         ← 전역 응답 래퍼
│       ├── config/
│       │   ├── FileUploadProperties.java
│       │   └── WebMvcConfig.java        ← CORS 설정
│       ├── exception/
│       │   ├── BusinessException.java
│       │   ├── ResourceNotFoundException.java
│       │   └── GlobalExceptionHandler.java  ← @RestControllerAdvice
│       └── security/
│           ├── JwtProperties.java
│           ├── JwtTokenProvider.java
│           ├── JwtAuthenticationFilter.java
│           ├── JwtAuthenticationEntryPoint.java
│           └── SecurityConfig.java          ← Spring Security 설정
│
├── module-user/                  ← 사용자/인증 모듈
│   └── src/main/java/com/company/module/user/
│       ├── entity/WebUser.java
│       ├── repository/WebUserRepository.java
│       ├── dto/{LoginRequest, LoginResponse, ChangePasswordRequest...}
│       ├── service/UserService.java
│       └── controller/UserController.java
│
├── module-fire/                  ← 소화기/소화전 도메인 모듈
│   └── src/main/java/com/company/module/fire/
│       ├── entity/{Building, Floor, Zone, ExtinguisherGroup,
│       │          Extinguisher, ExtinguisherInspection,
│       │          FireHydrant, FireHydrantInspection}
│       ├── repository/{Building, Floor, Extinguisher,
│       │              ExtinguisherInspection, FireHydrant,
│       │              FireHydrantInspection}Repository
│       ├── dto/{ExtinguisherResponse, ExtinguisherSaveRequest,
│       │       ExtinguisherInspectRequest,
│       │       FireHydrantResponse, FireHydrantSaveRequest}
│       ├── service/{ExtinguisherService, FireHydrantService}
│       └── controller/{ExtinguisherController, FireHydrantController}
│
├── module-sales/                 ← 신규 영업 업무 모듈
│   └── src/main/java/com/company/module/sales/
│       ├── entity/{SalesOrder, SalesOrderLine, OrderStatus}
│       ├── repository/{SalesOrderRepository, SalesOrderLineRepository}
│       ├── dto/{SalesOrderResponse, SalesOrderSaveRequest, SalesOrderSearchRequest}
│       ├── service/SalesOrderService.java
│       └── controller/SalesOrderController.java
│
└── sql/                          ← MariaDB DDL 스크립트
    ├── 01_schema.sql             ← DB/계정 생성
    ├── 02_ddl_core.sql           ← 기준 마스터 + 사용자 테이블
    ├── 03_ddl_fire.sql           ← 소화기/소화전 테이블 + 뷰
    ├── 04_ddl_sales.sql          ← module-sales 테이블 (MOD_SALES_*)
    └── 05_seed_data.sql          ← 초기 마스터 데이터
```

---

## API 엔드포인트 요약

### 인증 (module-user)
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | `/api/auth/login` | 로그인 (JWT 발급) | 모두 |
| POST | `/api/auth/change-password` | 비밀번호 변경 | 로그인 |
| GET | `/api/admin/users` | 사용자 목록 | ADMIN |
| POST | `/api/admin/users` | 사용자 등록 | ADMIN |
| DELETE | `/api/admin/users/{userId}` | 사용자 비활성화 | ADMIN |

### 소화기 (module-fire)
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| GET | `/fire-api/extinguishers` | 목록 조회 (검색/페이지) | 로그인 |
| GET | `/fire-api/extinguishers/{id}` | 상세 조회 + 점검이력 | 로그인 |
| POST | `/fire-api/extinguishers` | 등록/수정 | ADMIN |
| DELETE | `/fire-api/extinguishers/{id}` | 삭제 | ADMIN |
| POST | `/fire-api/extinguishers/inspect` | 점검 등록 | 로그인 |

### 소화전 (module-fire)
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| GET | `/fire-api/hydrants` | 목록 조회 | 로그인 |
| GET | `/fire-api/hydrants/{id}` | 상세 조회 + 점검이력 | 로그인 |
| POST | `/fire-api/hydrants` | 등록/수정 | ADMIN |
| DELETE | `/fire-api/hydrants/{id}` | 삭제 | ADMIN |
| POST | `/fire-api/hydrants/{id}/inspect` | 점검 등록 | 로그인 |

### 영업 주문 (module-sales)
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| GET | `/sales-api/orders` | 주문 목록 검색 | 로그인 |
| GET | `/sales-api/orders/{orderId}` | 주문 상세 | 로그인 |
| POST | `/sales-api/orders` | 주문 등록/수정 | 로그인 |
| POST | `/sales-api/orders/{orderId}/confirm` | 주문 확정 | ADMIN |
| POST | `/sales-api/orders/{orderId}/cancel` | 주문 취소 | 로그인 |
| DELETE | `/sales-api/orders/{orderId}` | 주문 삭제 (임시저장만) | ADMIN |

---

## DB 구조 (MariaDB)

### 기준 마스터
- `building` - 건물 마스터
- `floor` - 층 마스터
- `zone` - 구역 마스터

### 사용자
- `web_user` - 웹 사용자 (로그인 계정, BCrypt)

### 소화기 도메인
- `extinguisher_group` - 소화기 위치 그룹 (도면 마커)
- `extinguisher` - 소화기 (일련번호 EXT-000001, 교체주기 계산)
- `extinguisher_inspection` - 점검 이력 (최근 12건 유지, 당일 중복방지)

### 소화전 도메인
- `fire_hydrant` - 소화전 (Indoor/Outdoor, HYD-000001)
- `fire_hydrant_inspection` - 점검 이력

### 영업 모듈 (MOD_SALES_ prefix)
- `MOD_SALES_ORDER` - 주문 헤더
- `MOD_SALES_ORDER_LINE` - 주문 상세 라인

---

## 주요 설계 원칙

### module-sales 추가 전제 조건 준수
1. **Core 수정 금지**: `SecurityConfig`, `GlobalExceptionHandler` 등 core 소스 변경 없음
2. **기존 보안/예외처리 활용**: core의 `ApiResponse`, `BusinessException`, JWT 필터 그대로 사용
3. **Gradle 설정 방식 유지**: 각 모듈의 `build.gradle` 구조 동일
4. **URL Prefix**: `/sales-api/**`
5. **DB Prefix**: `MOD_SALES_`
6. **`@Transactional`**: Service 계층에서만 사용
7. **패키지**: `com.company.module.sales`
8. **계층 구조**: Controller → Service → Repository

### ASP.NET → Spring Boot 주요 변환 사항
| ASP.NET | Spring Boot |
|---------|-------------|
| Cookie Authentication | JWT Bearer (Stateless) |
| PBKDF2-SHA256 | BCrypt |
| SYSUTCDATETIME() | NOW() / @PrePersist |
| DB Computed Column (ReplacementDueDate) | @PrePersist/@PreUpdate |
| DB Sequence + DEFAULT | 서비스 레이어 일련번호 생성 |
| MSSQL OUTPUT Clause + TRIGGER | 일반 JPA Insert |
| vw_ExtinguisherList (View) | JPQL 직접 쿼리 (뷰는 레거시용 유지) |
| Role(Admin/User) | ROLE_ADMIN / ROLE_USER |

---

## 실행 방법

### 1. MariaDB 설정
```bash
# MariaDB 접속 후 순서대로 실행
mysql -u root -p < sql/01_schema.sql
mysql -u root -p < sql/02_ddl_core.sql
mysql -u root -p < sql/03_ddl_fire.sql
mysql -u root -p < sql/04_ddl_sales.sql
mysql -u root -p < sql/05_seed_data.sql
```

### 2. 관리자 비밀번호 초기화
```sql
-- BCrypt 해시 생성 후 직접 업데이트
UPDATE web_user
SET password_hash = '[BCryptPasswordEncoder().encode("비밀번호")]'
WHERE username = 'admin';
```

### 3. 빌드 및 실행
```bash
./gradlew clean build
./gradlew :core:bootRun
```

### 4. 환경변수 (운영)
```bash
export JWT_SECRET=your-256bit-secret-key
export UPLOAD_BASE_PATH=/var/fireweb/uploads
```

---

## 미구현 / 향후 개발 필요 사항
- [ ] QR 코드 생성 API (`/fire-api/extinguishers/{id}/qr`, `/fire-api/hydrants/{id}/qr`)
- [ ] 도면 이미지 업로드 API
- [ ] 소화기/소화전 이미지 업로드 연동 (multipart/form-data)
- [ ] OpenAI 점검 사진 분석 서비스 (`OpenAiInspectionAnalyzer` 포팅)
- [ ] 모바일 점검 페이지 API (`/m/**`)
- [ ] 소화기 Import(엑셀 업로드) API
- [ ] Principal → UserService 연동 (userId/displayName 조회)
- [ ] Refresh Token 구현
- [ ] 로그인 시도 제한 (Rate Limiting)
