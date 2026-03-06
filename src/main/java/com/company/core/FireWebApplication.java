package com.company.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * FireWeb Spring Boot application entry point.
 */
@SpringBootApplication(scanBasePackages = "com.company")
@EntityScan(basePackages = "com.company")
@EnableJpaRepositories(basePackages = "com.company")
public class FireWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireWebApplication.class, args);
    }
}