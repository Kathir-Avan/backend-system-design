package com.production.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * ============================================================
 * WEB MVC CONFIGURATION — WebConfig
 * ============================================================
 *
 * Configures cross-cutting concerns for the web layer:
 *   - CORS (Cross-Origin Resource Sharing)
 *
 * CORS EXPLAINED:
 *   Browsers block AJAX requests from one domain (origin) to another
 *   by default (Same-Origin Policy). CORS tells the browser which
 *   other origins are allowed to make requests to this API.
 *
 *   In production (Amazon, Netflix):
 *     - Frontend app (app.example.com) → API (api.example.com): needs CORS
 *     - Mobile apps / server-to-server: CORS doesn't apply (browser-only)
 *     - Internal microservice calls: use network policies, not CORS
 *
 * SECURITY NOTE:
 *   NEVER use allowedOrigins("*") in production for APIs that accept
 *   cookies or auth headers. Use explicit origin lists from config.
 *   The @Value injection reads from application.properties, so each
 *   environment (dev, staging, prod) has its own allowed origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Reads allowed origins from properties.
     * Default: localhost for local dev.
     * Production override: set app.cors.allowed-origins in prod properties.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    /**
     * CORS configuration bean.
     * Applied globally to all endpoints via UrlBasedCorsConfigurationSource.
     *
     * allowedMethods: restrict to the HTTP verbs your API actually uses.
     *   Including OPTIONS is REQUIRED — browsers send a preflight OPTIONS
     *   request before any cross-origin POST/PUT/DELETE.
     *
     * allowedHeaders: "Authorization" needed for JWT/OAuth tokens.
     *   "Content-Type" needed for request bodies.
     *   "*" is acceptable for headers (unlike origins) since header names
     *   don't create security risks.
     *
     * maxAge(3600): Browser caches the preflight response for 1 hour.
     *   Reduces OPTIONS preflight round-trips on repeat API calls.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Accept",
            "X-Requested-With", "X-Request-ID"   // X-Request-ID for distributed tracing
        ));
        config.setExposedHeaders(List.of("X-Total-Count", "X-Request-ID"));
        config.setAllowCredentials(true);  // Required for cookie-based auth
        config.setMaxAge(3600L);           // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);  // Apply only to API routes
        return source;
    }
}
