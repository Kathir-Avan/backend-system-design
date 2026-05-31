package com.production.app.service.impl;

import com.production.app.dao.ProductDAO;
import com.production.app.dto.ProductMapper;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.exception.DuplicateResourceException;
import com.production.app.exception.ResourceNotFoundException;
import com.production.app.model.Product;
import com.production.app.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================
 * SERVICE IMPLEMENTATION — ProductServiceImpl
 * ============================================================
 *
 * This class contains all business logic for the Product domain.
 * It is the only class that should be aware of both DTOs and entities.
 *
 * TRANSACTION STRATEGY:
 *   @Transactional(readOnly = true) at the class level —
 *     all methods are read-only by default (optimizes read performance:
 *     Hibernate skips dirty-checking, DB may use read replicas).
 *
 *   @Transactional (write) at the method level for mutations —
 *     overrides the class-level annotation for create/update/delete.
 *
 * TRANSACTION PROPAGATION:
 *   Default propagation is REQUIRED — the method joins an existing
 *   transaction if one exists, or creates a new one if not.
 *   This is the correct default for service layer methods.
 *
 * EXCEPTION HANDLING:
 *   Service throws domain exceptions (ResourceNotFoundException,
 *   DuplicateResourceException). The GlobalExceptionHandler in the
 *   controller layer converts these to HTTP 404/409 responses.
 *   Services should NEVER set HTTP status codes directly.
 *
 * DEPENDENCY INJECTION:
 *   @RequiredArgsConstructor generates a constructor for all `final` fields.
 *   Constructor injection is preferred over @Autowired because:
 *   - Works with immutable (final) fields
 *   - Makes dependencies explicit and testable
 *   - Detects circular dependencies at startup, not at runtime
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)   // Default: all reads optimized
public class ProductServiceImpl implements ProductService {

    /**
     * The active DAO implementation — either JpaProductDAO or
     * HibernateProductDAO depending on the active Spring profile.
     * This class doesn't know (or care) which one is injected.
     */
    private final ProductDAO productDAO;

    /**
     * MapStruct-generated mapper bean — injected by Spring.
     * Handles all entity ↔ DTO conversions cleanly.
     */
    private final ProductMapper productMapper;

    // ============================================================
    // CREATE
    // ============================================================

    /**
     * Create a new product with full business validation.
     *
     * BUSINESS RULES ENFORCED HERE:
     *   1. SKU must be globally unique (checked before persist)
     *   2. SKU is uppercased before storage for consistency
     *
     * @Transactional overrides the class-level readOnly=true.
     * rollbackFor=Exception.class ensures ALL exceptions (including
     * checked exceptions) trigger a rollback, not just RuntimeException.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        log.info("Creating product with SKU: {}", requestDTO.getSku());

        // BUSINESS RULE: SKU uniqueness — fail fast before DB insert
        String normalizedSku = requestDTO.getSku().toUpperCase().trim();
        if (productDAO.existsBySku(normalizedSku)) {
            log.warn("Duplicate SKU attempt: {}", normalizedSku);
            throw new DuplicateResourceException("Product", "SKU", normalizedSku);
        }

        // Normalize SKU before mapping
        requestDTO.setSku(normalizedSku);

        // MAP: DTO → Entity (MapStruct handles field assignment)
        Product product = productMapper.toEntity(requestDTO);

        // PERSIST
        Product savedProduct = productDAO.save(product);

        log.info("Product created successfully. ID: {}, SKU: {}",
                 savedProduct.getId(), savedProduct.getSku());

        // MAP: Entity → Response DTO (including computed inStock field)
        return productMapper.toResponseDTO(savedProduct);
    }

    // ============================================================
    // READ
    // ============================================================

    @Override
    public ProductResponseDTO getProductById(Long id) {
        log.debug("Fetching product by id: {}", id);
        Product product = productDAO.findById(id)
            .filter(Product::getActive)  // Treat soft-deleted as not found
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        return productMapper.toResponseDTO(product);
    }

    @Override
    public ProductResponseDTO getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        Product product = productDAO.findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "SKU", sku));

        return productMapper.toResponseDTO(product);
    }

    @Override
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        log.debug("Fetching all active products, page: {}", pageable.getPageNumber());
        return productDAO.findAllActive(pageable)
                         .map(productMapper::toResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getProductsByCategory(String category, Pageable pageable) {
        log.debug("Fetching products in category: '{}', page: {}", category, pageable.getPageNumber());
        return productDAO.findByCategory(category.trim(), pageable)
                         .map(productMapper::toResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice,
                                                             Pageable pageable) {
        // BUSINESS RULE: min price cannot exceed max price
        if (minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                "minPrice (" + minPrice + ") cannot be greater than maxPrice (" + maxPrice + ")");
        }

        log.debug("Fetching products in price range [{}, {}]", minPrice, maxPrice);
        return productDAO.findByPriceRange(minPrice, maxPrice, pageable)
                         .map(productMapper::toResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> searchProducts(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().length() < 2) {
            throw new IllegalArgumentException("Search keyword must be at least 2 characters");
        }

        log.debug("Searching products with keyword: '{}'", keyword);
        return productDAO.searchByName(keyword.trim(), pageable)
                         .map(productMapper::toResponseDTO);
    }

    @Override
    public List<ProductResponseDTO> getLowStockProducts(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");
        }

        log.debug("Fetching low-stock products (threshold: {})", threshold);
        return productMapper.toResponseDTOList(productDAO.findLowStockProducts(threshold));
    }

    // ============================================================
    // UPDATE
    // ============================================================

    /**
     * Full product update (PUT semantics — all fields replaced).
     *
     * PATTERN:
     *   1. Fetch existing entity → validates existence
     *   2. Apply DTO changes via MapStruct's updateEntityFromDTO
     *      (null fields in DTO are ignored due to NullValuePropertyMappingStrategy)
     *   3. Save the merged entity
     *
     * WHY NOT productDAO.save(productMapper.toEntity(dto))?
     *   That would create a NEW entity object missing audit fields
     *   (createdAt, updatedAt, active). Always load + merge.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO requestDTO) {
        log.info("Updating product id: {}", id);

        // Fetch existing — throws 404 if not found
        Product existingProduct = productDAO.findById(id)
            .filter(Product::getActive)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // If SKU is changing, validate the new SKU is not taken by another product
        String newSku = requestDTO.getSku().toUpperCase().trim();
        if (!newSku.equalsIgnoreCase(existingProduct.getSku()) && productDAO.existsBySku(newSku)) {
            throw new DuplicateResourceException("Product", "SKU", newSku);
        }
        requestDTO.setSku(newSku);

        // Apply changes (MapStruct merges non-null DTO fields into entity)
        productMapper.updateEntityFromDTO(requestDTO, existingProduct);

        // Save — JPA detects changes via dirty checking, issues UPDATE
        Product updatedProduct = productDAO.save(existingProduct);

        log.info("Product updated successfully. ID: {}", updatedProduct.getId());
        return productMapper.toResponseDTO(updatedProduct);
    }

    // ============================================================
    // DELETE
    // ============================================================

    /**
     * Soft-delete: marks product as inactive.
     * The record remains in the DB for audit and recovery purposes.
     * Hard deletes should be performed only via scheduled batch jobs
     * with compliance approval (GDPR right-to-erasure workflows).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        log.info("Soft-deleting product id: {}", id);

        int affected = productDAO.softDelete(id);

        if (affected == 0) {
            // No rows updated means the ID doesn't exist in the DB
            throw new ResourceNotFoundException("Product", "id", id);
        }

        log.info("Product id: {} soft-deleted successfully", id);
    }
}
