package com.production.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * DEPLOYMENT MODEL (Amazon/Netflix style):
 *   - Local dev  : mvn spring-boot:run -Dspring.profiles.active=dev
 *   - Production : java -jar app.jar --spring.profiles.active=prod
 *   - Docker     : ENV SPRING_PROFILES_ACTIVE=prod
 */
@SpringBootApplication
public class ProductionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductionApplication.class, args);
    }
}
