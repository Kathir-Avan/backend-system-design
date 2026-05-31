package com.production.app.service;

import com.production.app.dto.ApiResponse;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================
 * SERVICE INTERFACE — ProductService
 * ============================================================
 *
 * The service layer is the heart of the application.
 * It orchestrates business logic between the controller and DAO layers.
 *
 * RESPONSIBILITIES:
 *   1. Business logic (validate SKU uniqueness, enforce price rules)
 *   2. DTO ↔ Entity mapping (via ProductMapper)
 *   3. Transaction management (@Transactional)
 *   4. Error handling (throws domain exceptions)
 *   5. Coordination (could call multiple DAOs in a complex operation)
 *
 * WHY AN INTERFACE?
 *   - Enables easy mocking in unit tests
 *   - Allows multiple implementations (e.g., CachedProductService
 *     wrapping ProductServiceImpl with Redis caching)
 *   - Spring's @Transactional proxying works best with interfaces
 *
 * RETURN TYPES:
 *   Methods return DTOs, not entities. The controller and the outside
 *   world should never see raw entity objects.
 */
public interface ProductService {

    /**
     * Create a new product.
     * Validates that the SKU is unique before persisting.
     *
     * @param requestDTO validated product data from the API
     * @return response DTO of the created product
     * @throws com.production.app.exception.DuplicateResourceException if SKU already exists
     */
    ProductResponseDTO createProduct(ProductRequestDTO requestDTO);

    /**
     * Retrieve a product by its primary key.
     *
     * @param id the product's database ID
     * @return the product response DTO
     * @throws com.production.app.exception.ResourceNotFoundException if not found
     */
    ProductResponseDTO getProductById(Long id);

    /**
     * Retrieve a product by its SKU.
     *
     * @param sku case-insensitive SKU
     * @return the product response DTO
     * @throws com.production.app.exception.ResourceNotFoundException if not found
     */
    ProductResponseDTO getProductBySku(String sku);

    /**
     * List all active products with pagination and sorting.
     *
     * @param pageable page number, size, sort (from @PageableDefault or request params)
     * @return a page of product DTOs
     */
    Page<ProductResponseDTO> getAllProducts(Pageable pageable);

    /**
     * Filter products by category with pagination.
     */
    Page<ProductResponseDTO> getProductsByCategory(String category, Pageable pageable);

    /**
     * Filter products by price range.
     */
    Page<ProductResponseDTO> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice,
                                                      Pageable pageable);

    /**
     * Search products by name keyword.
     */
    Page<ProductResponseDTO> searchProducts(String keyword, Pageable pageable);

    /**
     * Get products with stock at or below the threshold.
     * Used for inventory management dashboards.
     */
    List<ProductResponseDTO> getLowStockProducts(int threshold);

    /**
     * Update an existing product (full update — replaces all fields).
     *
     * @param id         product to update
     * @param requestDTO new product data
     * @return updated product response DTO
     * @throws com.production.app.exception.ResourceNotFoundException if product not found
     */
    ProductResponseDTO updateProduct(Long id, ProductRequestDTO requestDTO);

    /**
     * Soft-delete a product (sets active=false).
     *
     * @param id product to deactivate
     * @throws com.production.app.exception.ResourceNotFoundException if product not found
     */
    void deleteProduct(Long id);
}
