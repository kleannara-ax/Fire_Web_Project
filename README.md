# FireWeb - Spring Boot 3.x Gradle Multi-Module Project

> ASP.NET Core 8.0 Razor Pages ??**Spring Boot 3.2.5 + Java 17 + MariaDB** ?„í™˜ ?„ë¡œ?íŠ¸

---

## ?„ë¡œ?íŠ¸ êµ¬ì¡°

```
fireweb/
?œâ??€ core/                          # ë©”ì¸ ? í”Œë¦¬ì??´ì…˜ ëª¨ë“ˆ (Spring Boot ì§„ì…??
??  ?œâ??€ src/main/java/com/company/core/
??  ??  ?œâ??€ FireWebApplication.java          # @SpringBootApplication
??  ??  ?œâ??€ common/ApiResponse.java          # ê³µí†µ API ?‘ë‹µ ?˜í¼
??  ??  ?œâ??€ config/
??  ??  ??  ?œâ??€ WebMvcConfig.java            # CORS ?¤ì •
??  ??  ??  ?”â??€ FileUploadProperties.java    # ?…ë¡œ??ê²½ë¡œ ?¤ì •
??  ??  ?œâ??€ exception/
??  ??  ??  ?œâ??€ GlobalExceptionHandler.java  # ?„ì—­ ?ˆì™¸ ì²˜ë¦¬ (@RestControllerAdvice)
??  ??  ??  ?œâ??€ BusinessException.java       # ë¹„ì¦ˆ?ˆìŠ¤ ?ˆì™¸ (400)
??  ??  ??  ?”â??€ ResourceNotFoundException.java  # ë¦¬ì†Œ???†ìŒ (404)
??  ??  ?”â??€ security/
??  ??      ?œâ??€ SecurityConfig.java          # Spring Security ?¤ì • (JWT Stateless)
??  ??      ?œâ??€ JwtTokenProvider.java        # JWT ?ì„±/ê²€ì¦???  ??      ?œâ??€ JwtAuthenticationFilter.java # JWT ?¸ì¦ ?„í„°
??  ??      ?œâ??€ JwtAuthenticationEntryPoint.java  # 401 JSON ?‘ë‹µ
??  ??      ?”â??€ JwtProperties.java           # JWT ?¤ì • ë°”ì¸????  ?”â??€ src/main/resources/
??      ?”â??€ application.yml                  # MariaDB, JPA, ë¡œê·¸ ?¤ì •
???œâ??€ module-user/                   # ?¬ìš©???¸ì¦ ëª¨ë“ˆ
??  ?”â??€ src/main/java/com/company/module/user/
??      ?œâ??€ controller/UserController.java   # /api/auth/**, /api/admin/users/**
??      ?œâ??€ service/UserService.java         # @Transactional (Service only)
??      ?œâ??€ repository/WebUserRepository.java
??      ?œâ??€ entity/WebUser.java              # web_user ?Œì´ë¸???      ?”â??€ dto/                             # LoginRequest/Response, UserCreateRequest ?????œâ??€ module-fire/                   # ?Œí™”ê¸??Œí™”???ì‚° ê´€ë¦?ëª¨ë“ˆ
??  ?”â??€ src/main/java/com/company/module/fire/
??      ?œâ??€ controller/
??      ??  ?œâ??€ ExtinguisherController.java  # /fire-api/extinguishers/**
??      ??  ?”â??€ FireHydrantController.java   # /fire-api/hydrants/**
??      ?œâ??€ service/
??      ??  ?œâ??€ ExtinguisherService.java     # @Transactional (Service only)
??      ??  ?”â??€ FireHydrantService.java
??      ?œâ??€ repository/                      # JpaRepository (countQuery ë¶„ë¦¬ ?ìš©)
??  ?”â??€ src/main/java/com/company/module/sales/
??      ?œâ??€ service/SalesOrderService.java         # @Transactional (Service only)
??      ?œâ??€ repository/                            # countQuery ë¶„ë¦¬ ?ìš©
??      ?œâ??€ entity/                                # SalesOrder (MOD_SALES_ORDER), SalesOrderLine
??      ?”â??€ dto/
???œâ??€ sql/
??  ?œâ??€ 01_schema.sql              # ?„ì²´ ?µí•© DDL (ê¶Œì¥ ?¬ìš©)
??  ?œâ??€ 02_ddl_core.sql            # core (web_user) DDL
??  ?œâ??€ 03_ddl_fire.sql            # fire ?„ë©”??DDL
??  ?œâ??€ 04_ddl_sales.sql           # sales ?„ë©”??DDL (MOD_SALES_*)
??  ?”â??€ 05_seed_data.sql           # ê¸°ì´ˆ/?˜í”Œ ?°ì´?????œâ??€ gradle/wrapper/
??  ?œâ??€ gradle-wrapper.jar
??  ?”â??€ gradle-wrapper.properties  # Gradle 8.7
?œâ??€ gradlew                        # Unix ?¤í–‰ ?¤í¬ë¦½íŠ¸
?œâ??€ gradlew.bat                    # Windows ?¤í–‰ ?¤í¬ë¦½íŠ¸
?œâ??€ settings.gradle                # ë©€??ëª¨ë“ˆ ? ì–¸
?”â??€ build.gradle                   # ë£¨íŠ¸ ê³µí†µ ?¤ì • (Java 17, Spring Boot 3.2.5 BOM)
```

---

## API ?”ë“œ?¬ì¸???”ì•½

### ?¸ì¦ (`module-user`)
| Method | URL | ?¤ëª… | ?¸ê? |
|--------|-----|------|------|
| POST | `/api/auth/login` | ë¡œê·¸????JWT ë°œê¸‰ | ê³µê°œ |
| POST | `/api/auth/change-password` | ë¹„ë?ë²ˆí˜¸ ë³€ê²?| ?¸ì¦ |
| GET | `/api/admin/users` | ?„ì²´ ?¬ìš©??ëª©ë¡ | ADMIN |
| POST | `/api/admin/users` | ?¬ìš©???±ë¡ | ADMIN |
| DELETE | `/api/admin/users/{id}` | ?¬ìš©??ë¹„í™œ?±í™” | ADMIN |

### ?Œí™”ê¸?(`module-fire`)
| Method | URL | ?¤ëª… | ?¸ê? |
|--------|-----|------|------|
| GET | `/fire-api/extinguishers` | ëª©ë¡ ì¡°íšŒ (?˜ì´ì§?ê²€?? | ?¸ì¦ |
| GET | `/fire-api/extinguishers/{id}` | ?ì„¸ ì¡°íšŒ (?ê??´ë ¥ ?¬í•¨) | ?¸ì¦ |
| POST | `/fire-api/extinguishers` | ?±ë¡/?˜ì • | ADMIN |
| POST | `/fire-api/extinguishers/inspect` | ?ê? ?±ë¡ | ?¸ì¦ |
| DELETE | `/fire-api/extinguishers/{id}` | ?? œ | ADMIN |

### ?Œí™”??(`module-fire`)
| Method | URL | ?¤ëª… | ?¸ê? |
|--------|-----|------|------|
| GET | `/fire-api/hydrants` | ëª©ë¡ ì¡°íšŒ (?˜ì´ì§?ê²€?? | ?¸ì¦ |
| GET | `/fire-api/hydrants/{id}` | ?ì„¸ ì¡°íšŒ (?ê??´ë ¥ ?¬í•¨) | ?¸ì¦ |
| POST | `/fire-api/hydrants` | ?±ë¡/?˜ì • | ADMIN |
| POST | `/fire-api/hydrants/{id}/inspect` | ?ê? ?±ë¡ | ?¸ì¦ |
| DELETE | `/fire-api/hydrants/{id}` | ?? œ | ADMIN |

| Method | URL | ?¤ëª… | ?¸ê? |
|--------|-----|------|------|

---

## ê¸°ìˆ  ?¤íƒ

| ??ª© | ?´ìš© |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Build | Gradle 8.7 (Multi-Module) |
| DB | MariaDB (JPA `ddl-auto: none`, ?˜ë™ DDL) |
| ?¸ì¦ | JWT (JJWT 0.12.6) Bearer Token |
| ë¹„ë?ë²ˆí˜¸ | BCrypt (Spring Security) |
| ORM | Spring Data JPA + Hibernate |
| ê²€ì¦?| Jakarta Validation (`@Valid`) |

---

## ?°ì´?°ë² ?´ìŠ¤ ?Œì´ë¸?
| ?Œì´ë¸?| ëª¨ë“ˆ | ?¤ëª… |
|--------|------|------|
| `web_user` | core/user | ?¬ìš©??ê³„ì • (BCrypt) |
| `building` | fire | ê±´ë¬¼ ë§ˆìŠ¤??|
| `floor` | fire | ì¸?ë§ˆìŠ¤??|
| `zone` | fire | êµ¬ì—­ ë§ˆìŠ¤??|
| `extinguisher_group` | fire | ?Œí™”ê¸??„ì¹˜ ê·¸ë£¹ |
| `extinguisher` | fire | ?Œí™”ê¸??ì‚° |
| `extinguisher_inspection` | fire | ?Œí™”ê¸??ê? ?´ë ¥ |
| `fire_hydrant` | fire | ?Œí™”???ì‚° |
| `fire_hydrant_inspection` | fire | ?Œí™”???ê? ?´ë ¥ |
| `MOD_SALES_ORDER` | sales | ?ì—… ì£¼ë¬¸ |
| `MOD_SALES_ORDER_LINE` | sales | ?ì—… ì£¼ë¬¸ ?ì„¸ ?¼ì¸ |

---

## ë¹ ë¥¸ ?œì‘

### 1. DB ?¤í‚¤ë§??ìš©
```bash
mysql -u root -p < sql/01_schema.sql
```

### 2. ?¤ì • ?˜ì •
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

### 3. ë¹Œë“œ ë°??¤í–‰
```bash
./gradlew :core:bootRun
# ?ëŠ”
./gradlew :core:bootJar
java -jar core/build/libs/fireweb-1.0.0.jar
```

---

## ?˜ì • ?´ë ¥

| ë²„ì „ | ?´ìš© |
|------|------|
| v1.1.0 | **ë²„ê·¸ ?˜ì •**: boolean ?„ë“œëª?ì¶©ëŒ(`isActive`??active`) ?˜ì •, JPQL countQuery ë¶„ë¦¬, gradlew ì¶”ê? |

---

## GitHub
- **Repository**: https://github.com/kleannara-ax/Fire_Web_Project
