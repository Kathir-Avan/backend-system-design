package com.production.app.service.impl;

import com.production.app.dao.ProductDAO;
import com.production.app.dto.ProductMapper;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.exception.DuplicateResourceException;
import com.production.app.exception.ResourceNotFoundException;
import com.production.app.model.Product;
import com.production.app.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
@Transactional(readOnly = true)   // Default: all reads optimized
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductDAO productDAO;
    private final ProductMapper productMapper;

    // Explicit constructor to initialize final fields (avoids Lombok dependency)
    public ProductServiceImpl(ProductDAO productDAO, ProductMapper productMapper) {
        this.productDAO = productDAO;
        this.productMapper = productMapper;
    }

    // ============================================================
    // CREATE
    // ============================================================

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
