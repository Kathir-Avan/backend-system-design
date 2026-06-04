package com.production.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration extracted from the main application class so
 * web-slice tests (@WebMvcTest) don't try to initialize JPA auditing.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
