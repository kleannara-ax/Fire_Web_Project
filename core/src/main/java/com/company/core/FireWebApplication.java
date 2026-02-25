package com.company.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * FireWeb Spring Boot Application 진입점
 * <p>
 * - 멀티 모듈 구조에서 core 모듈이 애플리케이션 메인을 담당
 * - 각 업무 모듈(module-user, module-fire, module-sales)의 Entity/Repository를 스캔
 * - Spring Security, 예외 처리, 공통 응답 포맷은 core에서 설정
 */
@SpringBootApplication(scanBasePackages = "com.company")
@EntityScan(basePackages = "com.company")
@EnableJpaRepositories(basePackages = "com.company")
public class FireWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireWebApplication.class, args);
    }
}
