package com.production.app.dto;

import lombok.*;

import java.util.List;

/**
 * ============================================================
 * GENERIC API RESPONSE WRAPPER
 * ============================================================
 *
 * Every API endpoint returns this envelope, not raw data.
 *
 * WHY AN ENVELOPE PATTERN?
 *   - Consistent structure across ALL endpoints. Clients always
 *     know to look in "data" for the payload and "message" for context.
 *   - Pagination metadata (totalElements, totalPages) can be added
 *     without breaking the existing "data" contract.
 *   - Error responses use the same wrapper with data=null and
 *     success=false — no special-casing in client code.
 *   - Netflix/Amazon APIs use this pattern for API versioning:
 *     the outer envelope is stable, the inner "data" schema can evolve.
 *
 * GENERIC TYPE <T>:
 *   Allows this one class to wrap any payload type:
 *     ApiResponse<ProductResponseDTO>        — single product
 *     ApiResponse<List<ProductResponseDTO>>  — list of products
 *     ApiResponse<PagedResult<ProductResponseDTO>> — paginated
 *
 * @param <T> The type of the data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** true for 2xx responses, false for 4xx/5xx */
    private boolean success;

    /** Human-readable message: "Product created successfully" */
    private String message;

    /** The actual payload. Null on error responses. */
    private T data;

    /**
     * HTTP status code mirrored in the body.
     * Useful for clients that cannot read HTTP headers (e.g., some proxies).
     */
    private int statusCode;

    // ============================================================
    // STATIC FACTORY METHODS — cleaner than calling the builder
    // every time in the controller/service layer.
    // ============================================================

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(201)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .statusCode(statusCode)
                .build();
    }
}
