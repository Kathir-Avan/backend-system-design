package com.production.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================
 * DOMAIN MODEL / JPA ENTITY — Product
 * ============================================================
 *
 * This class represents the "products" table in MySQL.
 * It is the DOMAIN object — the central truth of what a Product
 * is. It should NEVER be returned directly from an API endpoint.
 * Instead, it is mapped to/from a DTO at the service layer.
 *
 * WHY SEPARATE ENTITY AND DTO?
 *   - Entities may contain sensitive fields (audit metadata, internal
 *     flags) that should not be exposed via the API.
 *   - DTOs can be versioned independently of the database schema.
 *   - Decouples the persistence layer from the transport layer —
 *     a core principle at Amazon, PayPal, Netflix.
 *
 * LOMBOK ANNOTATIONS:
 *   @Data       — generates getters, setters, equals, hashCode, toString
 *   @Builder    — enables fluent builder pattern: Product.builder().name("x").build()
 *   @NoArgsConstructor — required by JPA (it uses reflection to instantiate)
 *   @AllArgsConstructor — required by @Builder when combined with @NoArgsConstructor
 *
 * JPA AUDITING:
 *   @EntityListeners(AuditingEntityListener.class) hooks into JPA's
 *   pre-persist and pre-update lifecycle events to auto-fill timestamps.
 *   Requires @EnableJpaAuditing on the main application class.
 */
@Entity
@Table(
    name = "products",
    indexes = {
        // Composite index on category + price supports common filtered queries.
        // Always define indexes at the entity level — Flyway mirrors them in DDL.
        @Index(name = "idx_products_category_price", columnList = "category, price"),
        @Index(name = "idx_products_sku", columnList = "sku", unique = true)
    }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    /**
     * Primary key.
     *
     * IDENTITY strategy delegates auto-increment to MySQL.
     * For distributed systems (multiple DB shards), you'd use
     * GenerationType.SEQUENCE with a custom allocationSize (e.g., 50)
     * to batch ID generation and reduce round-trips to the DB.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable unique product identifier (e.g., "PROD-001-BLK").
     *
     * nullable=false enforces NOT NULL at JPA schema generation level.
     * unique=true creates a UNIQUE constraint on the column.
     * length=50 maps to VARCHAR(50) — always set explicit lengths.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    /**
     * Product display name.
     * length=255 is the MySQL VARCHAR default but explicit is better.
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Long-form description. @Lob maps to TEXT in MySQL (up to 65KB).
     * For very large content, use MEDIUMTEXT or LONGTEXT via columnDefinition.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Price using BigDecimal — NEVER use float/double for money.
     * Floating-point arithmetic introduces rounding errors that are
     * unacceptable in financial systems (PayPal, Visa).
     *
     * precision=10, scale=2 → stores up to 99999999.99
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Stock quantity. int is sufficient (max ~2.1 billion units).
     * columnDefinition sets the MySQL default so even direct SQL
     * INSERTs without a stock value get 0 instead of NULL.
     */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer stockQuantity;

    /**
     * Product category (e.g., "Electronics", "Books").
     * In a full system this would be a @ManyToOne relationship
     * to a Category entity, but kept as String here for clarity.
     */
    @Column(nullable = false, length = 100)
    private String category;

    /**
     * Soft-delete flag. Production systems NEVER hard-delete records.
     * Instead, set active=false. This preserves audit trails, foreign
     * key references, and allows undo operations.
     */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean active = true;

    // ============================================================
    // AUDIT FIELDS — auto-populated by Spring Data JPA Auditing.
    // Every production table must have these columns.
    // ============================================================

    /**
     * Set automatically when the entity is first persisted.
     * updatable=false ensures this field is never changed on UPDATE.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated automatically on every merge/save operation.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
