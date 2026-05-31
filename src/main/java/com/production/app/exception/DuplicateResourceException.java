package com.production.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================
 * DOMAIN EXCEPTION — DuplicateResourceException
 * ============================================================
 *
 * Thrown when a client attempts to create a resource that
 * violates a uniqueness constraint (e.g., duplicate SKU).
 *
 * HTTP 409 Conflict is the correct status for "resource already exists".
 * (Do NOT use 400 Bad Request — the request format is valid,
 * it just conflicts with existing state.)
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
