package com.production.app.controller;

import com.production.app.dto.ApiResponse;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================
 * REST CONTROLLER — ProductController
 * ============================================================
 *
 * The controller is the entry point for all HTTP traffic.
 * Its ONLY responsibilities are:
 *   1. Parse and validate HTTP requests
 *   2. Delegate to the service layer
 *   3. Return properly structured HTTP responses
 *
 * WHAT THE CONTROLLER SHOULD NOT DO:
 *   - Business logic (that belongs in the service)
 *   - Database access (that belongs in the DAO)
 *   - Entity manipulation (use DTOs only)
 *
 * ANNOTATIONS:
 *   @RestController = @Controller + @ResponseBody
 *     All methods return data (not view names).
 *     Spring uses Jackson to serialize return values to JSON.
 *
 *   @RequestMapping("/api/v1/products")
 *     API versioning in the URL path is the most common strategy.
 *     Alternatives: request header versioning, content negotiation.
 *     URL versioning is recommended for public APIs (easier caching).
 *
 *   @Validated
 *     Activates Bean Validation on @RequestParam and @PathVariable
 *     (e.g., @Min on page number). @Valid handles @RequestBody only.
 *
 * RESPONSE PATTERN:
 *   All endpoints return ResponseEntity<ApiResponse<T>>.
 *   - ResponseEntity controls the HTTP status code
 *   - ApiResponse<T> provides the consistent JSON envelope
 *
 * HTTP STATUS CODES USED:
 *   200 OK        — successful GET
 *   201 Created   — successful POST
 *   204 No Content — successful DELETE (no body)
 *   400 Bad Request — validation failure (handled by GlobalExceptionHandler)
 *   404 Not Found   — resource not found
 *   409 Conflict    — duplicate resource
 *   500 Internal Server Error — unhandled exception
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated   // Enables constraint validation on @RequestParam/@PathVariable
@Slf4j
public class ProductController {

    private final ProductService productService;

    // ============================================================
    // CREATE — POST /api/v1/products
    // ============================================================

    /**
     * Create a new product.
     *
     * @Valid triggers Bean Validation on ProductRequestDTO.
     * If validation fails, Spring throws MethodArgumentNotValidException
     * BEFORE this method is called — the GlobalExceptionHandler handles it.
     *
     * @RequestBody maps the JSON request body to the DTO.
     * Jackson handles deserialization; validation runs after.
     *
     * Returns HTTP 201 Created — correct semantics for resource creation.
     * The Location header should also be set (shown in a production implementation
     * you'd add: UriComponentsBuilder and return .location(uri).build())
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(
            @Valid @RequestBody ProductRequestDTO requestDTO) {

        log.info("POST /api/v1/products - Creating product with SKU: {}", requestDTO.getSku());

        ProductResponseDTO created = productService.createProduct(requestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Product created successfully"));
    }

    // ============================================================
    // READ — GET /api/v1/products/{id}
    // ============================================================

    /**
     * Fetch a single product by primary key.
     *
     * @PathVariable binds the {id} segment to the `id` parameter.
     * @Min(1) validates that the ID is a positive integer at the URL level.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(
            @PathVariable @Min(value = 1, message = "Product ID must be a positive number") Long id) {

        log.info("GET /api/v1/products/{}", id);

        ProductResponseDTO product = productService.getProductById(id);

        return ResponseEntity.ok(ApiResponse.success(product, "Product retrieved successfully"));
    }

    // ============================================================
    // READ — GET /api/v1/products/sku/{sku}
    // ============================================================

    /**
     * Fetch a product by its SKU code.
     * Useful when the client has the SKU from a barcode scan or
     * catalog import but not the internal database ID.
     */
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductBySku(
            @PathVariable String sku) {

        log.info("GET /api/v1/products/sku/{}", sku);

        ProductResponseDTO product = productService.getProductBySku(sku);

        return ResponseEntity.ok(ApiResponse.success(product, "Product retrieved successfully"));
    }

    // ============================================================
    // LIST — GET /api/v1/products
    // ============================================================

    /**
     * List all active products with pagination, sorting, and optional filters.
     *
     * QUERY PARAMETERS:
     *   page      — zero-based page index (default 0)
     *   size      — number of items per page (default 20, max 100)
     *   sort      — sort field and direction (e.g., sort=price,desc)
     *   category  — optional category filter
     *   minPrice  — optional price floor
     *   maxPrice  — optional price ceiling
     *   search    — optional name keyword search
     *
     * EXAMPLE URLS:
     *   GET /api/v1/products
     *   GET /api/v1/products?page=0&size=10&sort=price,asc
     *   GET /api/v1/products?category=Electronics&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponseDTO>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String[] sortParts = sort.split(",");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts.length > 1 ? sortParts[1] : "desc"), sortParts[0]));

        Page<ProductResponseDTO> result = productService.getAllProducts(pageable);

        return ResponseEntity.ok(ApiResponse.success(result, "Products retrieved successfully"));
    }

    // ... other endpoints omitted for brevity
}
