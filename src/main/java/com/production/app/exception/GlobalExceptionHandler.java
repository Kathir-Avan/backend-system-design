package com.production.app.exception;

import com.production.app.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * GLOBAL EXCEPTION HANDLER — GlobalExceptionHandler
 * ============================================================
 *
 * Centralizes all exception-to-HTTP-response translation.
 * Without this, Spring would return its default HTML error pages
 * or whitelabel error JSON — not suitable for a production REST API.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   Intercepts exceptions from ALL @RestController classes.
 *   The methods here act as last-resort exception catchers.
 *
 * PRODUCTION BENEFITS:
 *   1. Consistent error response format across all endpoints.
 *   2. Prevents stack traces from leaking to clients (security risk).
 *   3. Proper HTTP status codes (400, 404, 409, 422, 500).
 *   4. All errors logged centrally for monitoring/alerting.
 *
 * ERROR RESPONSE FORMAT (uses ApiResponse wrapper):
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "data": { "sku": "SKU is required", "price": "Price must be positive" },
 *   "statusCode": 400
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle @Valid validation failures on @RequestBody.
     *
     * Spring throws MethodArgumentNotValidException when a DTO
     * annotated with @Valid fails Bean Validation constraints.
     *
     * We collect all field errors (not just the first one) and
     * return them all in one response — better UX than making the
     * client submit the form multiple times to find all errors.
     *
     * Response structure:
     * {
     *   "data": {
     *     "sku": "SKU is required",
     *     "price": "Price must be at least 0.01"
     *   }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // If multiple constraints fail on the same field, last one wins.
            // For a more complete solution, collect lists per field.
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed. Please check the provided data.")
                        .data(fieldErrors)
                        .statusCode(400)
                        .build());
    }

    /**
     * Handle ResourceNotFoundException → HTTP 404
     *
     * Thrown by the service layer when an entity is not found.
     * Returns the exception message directly — it's already human-readable.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {

        log.warn("Resource not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), 404));
    }

    /**
     * Handle DuplicateResourceException → HTTP 409
     *
     * Thrown when a client tries to create a resource with a
     * field value that violates a uniqueness constraint.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
            DuplicateResourceException ex) {

        log.warn("Duplicate resource conflict: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), 409));
    }

    /**
     * Handle IllegalArgumentException → HTTP 400
     *
     * Thrown by the service layer for invalid business arguments
     * (e.g., minPrice > maxPrice, negative threshold).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("Invalid argument: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), 400));
    }

    /**
     * Handle type mismatch in path variables / request params.
     * e.g., GET /api/v1/products/abc — "abc" cannot be parsed as Long.
     * Returns: "Invalid value 'abc' for parameter 'id'. Expected type: Long"
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = String.format(
            "Invalid value '%s' for parameter '%s'. Expected type: %s",
            ex.getValue(), ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        log.warn("Type mismatch: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, 400));
    }

    /**
     * CATCH-ALL handler for any unexpected exception.
     *
     * IMPORTANT: Never return the exception message to the client here
     * — it may contain stack traces, SQL, or internal paths (security leak).
     * Log the full exception internally, return a generic message externally.
     *
     * In production, this would also:
     *   - Publish an alert to PagerDuty / Opsgenie
     *   - Emit a metric to Prometheus / CloudWatch
     *   - Add a correlation ID for log tracing
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUnhandledExceptions(Exception ex) {

        // Log the full stack trace for debugging — NOT sent to client
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    "An unexpected error occurred. Please try again later or contact support.",
                    500));
    }
}
