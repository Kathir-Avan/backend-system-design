package com.production.app.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * ============================================================
 * REQUEST DTO — ProductRequestDTO
 * ============================================================
 *
 * This is the object that clients send in the HTTP request body
 * (POST /api/products or PUT /api/products/{id}).
 *
 * PURPOSE OF A SEPARATE REQUEST DTO:
 *   1. SECURITY: Clients cannot set fields like `id`, `createdAt`,
 *      or `active` — those are server-controlled.
 *   2. VALIDATION: Bean Validation annotations (@NotBlank, @Min, etc.)
 *      live here, not on the entity. Keeps domain logic clean.
 *   3. VERSIONING: v1 and v2 APIs can have different DTOs while
 *      sharing the same entity — critical for backward compatibility.
 *   4. DOCUMENTATION: Swagger/OpenAPI reads these annotations and
 *      automatically generates accurate API docs.
 *
 * LOMBOK:
 *   @Data        — getters, setters, equals, hashCode, toString
 *   @Builder     — fluent construction in tests/service layer
 *   @NoArgsConstructor / @AllArgsConstructor — Jackson deserialization
 *   requires a no-args constructor; Builder needs AllArgs.
 *
 * VALIDATION ANNOTATIONS (Bean Validation / JSR-380):
 *   These are checked when @Valid is used in the controller parameter.
 *   If any fail, Spring throws MethodArgumentNotValidException, which
 *   our GlobalExceptionHandler converts into a 400 response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    /**
     * @NotBlank rejects null, empty string "", and whitespace-only " ".
     * @Pattern enforces the SKU format: letters, digits, and hyphens only.
     * Example valid SKU: "PROD-001-BLK"
     */
    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9\\-]{3,50}$",
             message = "SKU must be 3-50 uppercase alphanumeric characters or hyphens")
    private String sku;

    /**
     * @Size limits string length at the DTO layer — catches oversized
     * input before it even reaches the database.
     */
    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    /**
     * Description is optional (no @NotBlank), but if provided,
     * it cannot exceed 5000 characters.
     */
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    /**
     * @DecimalMin ensures price is positive.
     * inclusive=false means 0.00 is NOT allowed (must be > 0).
     * @Digits(integer=8, fraction=2) ensures at most 8 integer digits
     * and 2 decimal places — prevents overflow and rounding errors.
     */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Price must be at least 0.01")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal price;

    /**
     * @Min(0) prevents negative stock values.
     * Default is 0 (set at service layer if null).
     */
    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Max(value = 1_000_000, message = "Stock quantity cannot exceed 1,000,000")
    private Integer stockQuantity;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category cannot exceed 100 characters")
    private String category;
}
