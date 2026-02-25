# FireWeb - Spring Boot Multi-Module Project

> 소화기 / 소화전 자산 관리, 도면 좌표 매핑, 점검 이력 관리, QR 기반 조회/점검 시스템  
> ASP.NET Core Razor Pages (.NET 8) → **Spring Boot 3.x + Java 17 + MariaDB** 전환

---

## 프로젝트 구조

```
fireweb/
├── core/                          # Application 진입점, Security, 공통 예외처리
├── module-user/                   # 사용자/권한 관리
├── module-fire/                   # 소화기 / 소화전 자산 관리 (기존 FireWeb 핵심)
├── module-sales/                  # 영업 주문 관리 (신규 업무 모듈)
├── sql/
│   └── 01_schema.sql              # MariaDB DDL 스크립트 (JPA ddl-auto: none)
├── build.gradle                   # 루트 빌드 설정
└── settings.gradle                # 멀티 모듈 정의
```

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Build | Gradle Multi-Module |
| Security | Spring Security + JWT (Bearer) |
| ORM | Spring Data JPA (ddl-auto: **none**) |
| DB | MariaDB |
| Logging | Logback (Spring Boot 기본) |
| Base Package | `com.company` |

---

## 모듈 구성

### `core`
- **역할**: Spring Boot Application 진입점, 전역 설정
- **주요 클래스**:
  - `FireWebApplication` — `@SpringBootApplication` 진입점
  - `SecurityConfig` — JWT Stateless 인증, Role 기반 인가
  - `JwtTokenProvider` — JWT 생성/검증
  - `JwtAuthenticationFilter` — Bearer 토큰 필터
  - `GlobalExceptionHandler` — 전역 예외 → `ApiResponse` 통일
  - `ApiResponse<T>` — 공통 응답 포맷 `{ ok, message, data }`
- **수정 금지** (업무 모듈에서 Core 소스 직접 수정 불가)

### `module-user`
- **패키지**: `com.company.module.user`
- **테이블**: `web_user`
- **기능**: 로그인(JWT 발급), 비밀번호 변경, 사용자 관리(Admin)
- **주요 API**:
  | Method | URL | 설명 |
  |--------|-----|------|
  | POST | `/api/auth/login` | 로그인 → JWT 토큰 발급 |
  | POST | `/api/auth/change-password` | 비밀번호 변경 |
  | GET | `/api/admin/users` | 전체 사용자 목록 (Admin) |
  | POST | `/api/admin/users` | 사용자 등록 (Admin) |
  | DELETE | `/api/admin/users/{id}` | 사용자 비활성화 (Admin) |

### `module-fire`
- **패키지**: `com.company.module.fire`
- **테이블**: `building`, `floor`, `zone`, `extinguisher_group`, `extinguisher`, `extinguisher_inspection`, `fire_hydrant`, `fire_hydrant_inspection`
- **기능**: 소화기/소화전 자산 관리, 점검 이력(최근 12건 유지), 도면 좌표 매핑
- **주요 API**:
  | Method | URL | 설명 |
  |--------|-----|------|
  | GET | `/fire-api/extinguishers` | 소화기 목록 (검색/필터/페이징) |
  | GET | `/fire-api/extinguishers/{id}` | 소화기 상세 + 점검 이력 |
  | POST | `/fire-api/extinguishers` | 소화기 등록/수정 (Admin) |
  | POST | `/fire-api/extinguishers/inspect` | 소화기 점검 등록 |
  | DELETE | `/fire-api/extinguishers/{id}` | 소화기 삭제 (Admin) |
  | GET | `/fire-api/hydrants` | 소화전 목록 |
  | GET | `/fire-api/hydrants/{id}` | 소화전 상세 + 점검 이력 |
  | POST | `/fire-api/hydrants` | 소화전 등록/수정 (Admin) |
  | POST | `/fire-api/hydrants/{id}/inspect` | 소화전 점검 등록 |
  | DELETE | `/fire-api/hydrants/{id}` | 소화전 삭제 (Admin) |

### `module-sales` _(신규)_
- **패키지**: `com.company.module.sales`
- **테이블 Prefix**: `MOD_SALES_`
- **테이블**: `MOD_SALES_ORDER`, `MOD_SALES_ORDER_LINE`
- **기능**: 영업 주문 관리 (등록/확정/취소/삭제)
- **주요 API**:
  | Method | URL | 설명 |
  |--------|-----|------|
  | GET | `/sales-api/orders` | 주문 목록 (검색/필터/페이징) |
  | GET | `/sales-api/orders/{id}` | 주문 상세 + 주문 라인 |
  | POST | `/sales-api/orders` | 주문 등록/수정 |
  | POST | `/sales-api/orders/{id}/confirm` | 주문 확정 |
  | POST | `/sales-api/orders/{id}/cancel` | 주문 취소 |
  | DELETE | `/sales-api/orders/{id}` | 주문 삭제 (Admin, DRAFT 상태만) |

---

## 신규 모듈 추가 가이드 (`module-sales` 패턴 따라하기)

1. `settings.gradle`에 `include 'module-xxx'` 추가
2. `module-xxx/build.gradle` 생성 (bootJar=false, jar=true)
3. `core/build.gradle`에 `implementation project(':module-xxx')` 추가
4. 패키지 네임스페이스: `com.company.module.xxx`
5. URL Prefix: 도메인에 맞는 prefix 사용 (예: `/xxx-api/**`)
6. DB Table Prefix: 협의된 prefix 사용 (예: `MOD_XXX_`)
7. **Core 소스 절대 수정 금지** — Security, 예외처리 등 Core 제공 Bean 그대로 활용
8. `@Transactional`은 **Service 계층에서만** 사용

---

## 데이터베이스 설정

### DDL 실행
```bash
# MariaDB에 접속 후 DDL 실행
mysql -u root -p < sql/01_schema.sql
```

### application.yml 설정
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/fireweb?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: fireweb
    password: fireweb1234
  jpa:
    hibernate:
      ddl-auto: none   # ← 반드시 none 유지
```

### 환경변수 (운영 시 반드시 설정)
```bash
export JWT_SECRET=your-production-secret-key-256bit
export UPLOAD_BASE_PATH=/data/fireweb/uploads
```

---

## 빌드 & 실행

```bash
# 전체 빌드
./gradlew build

# core 모듈 실행 (모든 업무 모듈 포함)
./gradlew :core:bootRun

# 특정 모듈 테스트
./gradlew :module-sales:test
```

---

## 기존 ASP.NET → Spring Boot 전환 매핑

| ASP.NET | Spring Boot |
|---------|-------------|
| Cookie 인증 | JWT Bearer 토큰 |
| PBKDF2-SHA256 | BCrypt |
| Razor Pages | REST API (Controller) |
| `FireDbContext` (SQL Server) | Spring Data JPA (MariaDB) |
| `Serilog` 롤링 파일 | Logback (`logs/fireweb.log`) |
| `Program.cs` | `FireWebApplication.java` + `SecurityConfig.java` |
| `[Authorize(Roles="Admin")]` | `@PreAuthorize("hasRole('ADMIN')")` |
| Anti-forgery | JWT Stateless (CSRF 미적용) |

---

## 주의사항

- **Core 소스 수정 금지**: 모든 업무 모듈은 Core가 제공하는 `SecurityConfig`, `GlobalExceptionHandler`, `ApiResponse`를 그대로 사용
- **JPA ddl-auto: none**: 스키마 변경 시 반드시 `sql/` 하위에 DDL을 작성하고 직접 실행
- **@Transactional**: Service 계층에서만 사용 (Controller, Repository 적용 금지)
- **JWT Secret**: 운영 환경에서 반드시 환경변수 `JWT_SECRET`으로 재정의

---

_Last Updated: 2026-02-25_
