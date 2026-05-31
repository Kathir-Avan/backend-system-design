package com.production.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ============================================================
 * APPLICATION ENTRY POINT
 * ============================================================
 *
 * @SpringBootApplication is a meta-annotation that combines:
 *   1. @Configuration       — marks this as a Spring config class
 *   2. @EnableAutoConfiguration — triggers Spring Boot's magic
 *      (auto-configures DataSource, JPA, Jackson, Actuator, etc.)
 *   3. @ComponentScan       — scans com.production.app and all
 *      sub-packages for Spring-managed beans
 *
 * @EnableJpaAuditing activates automatic population of audit fields
 * like @CreatedDate and @LastModifiedDate on every entity save.
 * This is a production standard — every table needs an audit trail.
 *
 * DEPLOYMENT MODEL (Amazon/Netflix style):
 *   - Local dev  : mvn spring-boot:run -Dspring.profiles.active=dev
 *   - Production : java -jar app.jar --spring.profiles.active=prod
 *   - Docker     : ENV SPRING_PROFILES_ACTIVE=prod
 */
@SpringBootApplication
@EnableJpaAuditing
public class ProductionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductionApplication.class, args);
    }
}
