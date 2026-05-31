package com.production.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================
 * DOMAIN EXCEPTION — ResourceNotFoundException
 * ============================================================
 *
 * Thrown by the service layer when a requested resource (product,
 * user, order, etc.) does not exist in the database.
 *
 * @ResponseStatus(HttpStatus.NOT_FOUND): When this exception is
 * thrown from a @RestController, Spring will return HTTP 404
 * automatically IF there is no @ExceptionHandler for it.
 * Our GlobalExceptionHandler overrides this with a richer response.
 *
 * DESIGN PATTERN — "Tell, Don't Ask":
 *   Instead of returning null and making callers check for it,
 *   we throw a meaningful exception. This makes bugs obvious
 *   immediately rather than causing NullPointerExceptions elsewhere.
 *
 * NAMING CONVENTION:
 *   Resource   = entity type ("Product", "User", "Order")
 *   FieldName  = the field searched on ("id", "SKU", "email")
 *   FieldValue = the actual value that wasn't found
 *
 * Example message: "Product not found with id: 42"
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        // Call RuntimeException with a human-readable message
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    // Getters for use in GlobalExceptionHandler response building
    public String getResourceName() { return resourceName; }
    public String getFieldName()    { return fieldName; }
    public Object getFieldValue()   { return fieldValue; }
}
