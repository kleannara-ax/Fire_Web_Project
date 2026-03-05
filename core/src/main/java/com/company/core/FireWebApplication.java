package com.company.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * FireWeb Spring Boot Application ì§„ì…?? * <p>
 * - ë©€??ëª¨ë“ˆ êµ¬ì¡°?ì„œ core ëª¨ë“ˆ??? í”Œë¦¬ì??´ì…˜ ë©”ì¸???´ë‹¹
 * - ê°??…ë¬´ ëª¨ë“ˆ(module-user, module-fire)??Entity/Repositoryë¥??¤ìº”
 * - Spring Security, ?ˆì™¸ ì²˜ë¦¬, ê³µí†µ ?‘ë‹µ ?¬ë§·?€ core?ì„œ ?¤ì •
 */
@SpringBootApplication(scanBasePackages = "com.company")
@EntityScan(basePackages = "com.company")
@EnableJpaRepositories(basePackages = "com.company")
public class FireWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireWebApplication.class, args);
    }
}
