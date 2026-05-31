package com.production.app.dao.jpa;

import com.production.app.dao.ProductDAO;
import com.production.app.model.Product;
import com.production.app.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * JPA DAO IMPLEMENTATION — JpaProductDAO
 * ============================================================
 *
 * This implementation of ProductDAO uses Spring Data JPA
 * (backed by Hibernate as the JPA provider) for all DB operations.
 *
 * PROFILE-BASED ACTIVATION:
 *   @Profile({"default", "dev", "jpa", "prod"}) — this bean is
 *   active when the Spring profile is "default", "dev", "jpa", or "prod".
 *
 *   To switch to the native Hibernate implementation instead, run:
 *     -Dspring.profiles.active=hibernate
 *
 *   The service layer is completely unaware of which implementation
 *   is active — it only knows the ProductDAO interface.
 *
 * THIS vs REPOSITORY:
 *   This class is a thin wrapper around ProductRepository.
 *   Its value is:
 *     - Enforces the ProductDAO contract (the interface)
 *     - Adds logging at the DAO boundary (all DB operations are logged)
 *     - Allows mixing JPA with custom logic (caching, metrics) without
 *       modifying ProductRepository
 *
 * @Slf4j: Lombok generates a static `log` field using SLF4J.
 *         Logs go to Logback by default (configured in logback-spring.xml).
 *         In production, logs are shipped to ELK/Splunk/CloudWatch.
 *
 * @RequiredArgsConstructor: Lombok generates a constructor that injects
 *         all `final` fields — a Spring best practice over @Autowired.
 */
@Repository
@Profile({"default", "dev", "jpa", "prod"})
@Slf4j
@RequiredArgsConstructor
public class JpaProductDAO implements ProductDAO {

    /**
     * The Spring Data JPA repository — all actual DB interaction
     * happens through this interface's auto-generated implementation.
     */
    private final ProductRepository productRepository;

    /**
     * Persist a new product.
     *
     * JpaRepository.save() calls entityManager.persist() for new entities
     * (id is null) and entityManager.merge() for existing ones.
     * After save(), JPA flushes to the DB within the transaction boundary
     * and the returned entity has the auto-generated id populated.
     */
    @Override
    public Product save(Product product) {
        log.debug("[JPA-DAO] Saving product with SKU: {}", product.getSku());
        Product saved = productRepository.save(product);
        log.info("[JPA-DAO] Product saved successfully with id: {}", saved.getId());
        return saved;
    }

    /**
     * Find by primary key.
     * Returns Optional to avoid null returns — callers must
     * explicitly handle the "not found" case.
     */
    @Override
    public Optional<Product> findById(Long id) {
        log.debug("[JPA-DAO] Looking up product by id: {}", id);
        return productRepository.findById(id);
    }

    /**
     * Find by SKU (case-insensitive).
     * The custom JPQL query in the repository handles the
     * LOWER(sku) comparison.
     */
    @Override
    public Optional<Product> findBySku(String sku) {
        log.debug("[JPA-DAO] Looking up product by SKU: {}", sku);
        return productRepository.findBySkuIgnoreCase(sku);
    }

    /**
     * SKU existence check for duplicate prevention.
     */
    @Override
    public boolean existsBySku(String sku) {
        log.debug("[JPA-DAO] Checking SKU existence: {}", sku);
        return productRepository.existsBySkuIgnoreCase(sku);
    }

    /**
     * Paginated list of all active products.
     * NEVER call findAll() without pagination in production.
     */
    @Override
    public Page<Product> findAllActive(Pageable pageable) {
        log.debug("[JPA-DAO] Fetching active products, page: {}, size: {}",
                  pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findByActiveTrue(pageable);
    }

    /**
     * Category-filtered paginated list.
     */
    @Override
    public Page<Product> findByCategory(String category, Pageable pageable) {
        log.debug("[JPA-DAO] Fetching products for category: {}", category);
        return productRepository.findByCategoryAndActiveTrue(category, pageable);
    }

    /**
     * Price range filter.
     */
    @Override
    public Page<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice,
                                           Pageable pageable) {
        log.debug("[JPA-DAO] Fetching products in price range [{}, {}]", minPrice, maxPrice);
        return productRepository.findByPriceBetweenAndActiveTrue(minPrice, maxPrice, pageable);
    }

    /**
     * Name search.
     */
    @Override
    public Page<Product> searchByName(String name, Pageable pageable) {
        log.debug("[JPA-DAO] Searching products by name: '{}'", name);
        return productRepository.searchByName(name, pageable);
    }

    /**
     * Low-stock finder.
     */
    @Override
    public List<Product> findLowStockProducts(int threshold) {
        log.debug("[JPA-DAO] Finding low-stock products (threshold: {})", threshold);
        return productRepository.findLowStockProducts(threshold);
    }

    /**
     * Soft-delete.
     * Returns the number of rows affected — 0 means the product ID
     * was not found, which the service layer converts to a 404.
     */
    @Override
    public int softDelete(Long id) {
        log.info("[JPA-DAO] Soft-deleting product id: {}", id);
        int affected = productRepository.softDeleteById(id);
        log.info("[JPA-DAO] Soft-delete affected {} row(s) for id: {}", affected, id);
        return affected;
    }
}
