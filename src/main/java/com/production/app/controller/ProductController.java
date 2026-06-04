package com.production.app.controller;

import com.production.app.dto.ApiResponse;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * ============================================================
 * REST CONTROLLER — ProductController
 * ============================================================
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated   // Enables constraint validation on @RequestParam/@PathVariable
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    // Explicit constructor (avoids reliance on Lombok-generated constructor during builds)
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // CREATE — POST /api/v1/products
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(
            @Valid @RequestBody ProductRequestDTO requestDTO) {

        log.info("POST /api/v1/products - Creating product with SKU: {}", requestDTO.getSku());

        ProductResponseDTO created = productService.createProduct(requestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Product created successfully"));
    }

    // READ — GET /api/v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(
            @PathVariable @Min(value = 1, message = "Product ID must be a positive number") Long id) {

        log.info("GET /api/v1/products/{}", id);

        ProductResponseDTO product = productService.getProductById(id);

        return ResponseEntity.ok(ApiResponse.success(product, "Product retrieved successfully"));
    }

    // READ — GET /api/v1/products/sku/{sku}
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductBySku(
            @PathVariable String sku) {

        log.info("GET /api/v1/products/sku/{}", sku);

        ProductResponseDTO product = productService.getProductBySku(sku);

        return ResponseEntity.ok(ApiResponse.success(product, "Product retrieved successfully"));
    }

    // LIST — GET /api/v1/products
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
