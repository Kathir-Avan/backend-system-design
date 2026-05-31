package com.production.app.dao;

import com.production.app.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * DAO INTERFACE — ProductDAO
 * ============================================================
 *
 * The DAO (Data Access Object) pattern adds an abstraction layer
 * between the SERVICE layer and the actual persistence mechanism.
 *
 * WHY A DAO INTERFACE ON TOP OF THE REPOSITORY?
 *
 *   You might ask: "We already have ProductRepository — why add
 *   another layer?" Here's the production-grade reasoning:
 *
 *   1. TECHNOLOGY SWITCHING: This project demonstrates switching
 *      between Spring Data JPA and native Hibernate. The service
 *      layer calls ProductDAO; the active profile determines whether
 *      JpaProductDAO or HibernateProductDAO is injected. Zero changes
 *      to the service or controller.
 *
 *   2. TESTABILITY: Unit tests inject a MockProductDAO instead of
 *      spinning up a real database. Clean, fast, isolated tests.
 *
 *   3. CUSTOM LOGIC: The DAO can encapsulate complex persistence
 *      logic (caching, retry, multi-source routing) that doesn't
 *      belong in a Spring Data interface.
 *
 *   4. INTERFACE SEGREGATION: Different service classes may need
 *      different subsets of DB operations. Multiple narrow DAO
 *      interfaces are better than one fat repository.
 *
 * INTERFACE DESIGN PRINCIPLE:
 *   Methods here are named for what the BUSINESS needs, not how
 *   the database works. "findActivePaginated" is more meaningful
 *   than "findByActiveTrue".
 */
public interface ProductDAO {

    /**
     * Persist a new product to the database.
     *
     * @param product the entity to save (id must be null — DB assigns it)
     * @return the saved entity with the DB-assigned id populated
     */
    Product save(Product product);

    /**
     * Retrieve a single active product by its primary key.
     *
     * @param id the product's primary key
     * @return Optional.empty() if not found or soft-deleted
     */
    Optional<Product> findById(Long id);

    /**
     * Retrieve a product by its unique SKU code.
     *
     * @param sku case-insensitive product SKU
     * @return Optional.empty() if not found
     */
    Optional<Product> findBySku(String sku);

    /**
     * Check if a SKU already exists (for uniqueness validation before insert).
     *
     * @param sku the SKU to check
     * @return true if at least one product with this SKU exists
     */
    boolean existsBySku(String sku);

    /**
     * List all active products with pagination.
     *
     * @param pageable pagination and sort parameters
     * @return a page of products
     */
    Page<Product> findAllActive(Pageable pageable);

    /**
     * List active products filtered by category.
     *
     * @param category  category name
     * @param pageable  pagination params
     * @return a filtered, paginated page
     */
    Page<Product> findByCategory(String category, Pageable pageable);

    /**
     * List products within a price range (inclusive).
     *
     * @param minPrice lower bound (inclusive)
     * @param maxPrice upper bound (inclusive)
     * @param pageable pagination params
     * @return filtered, paginated page
     */
    Page<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    /**
     * Full-text search on product name (case-insensitive LIKE).
     *
     * @param name     search keyword
     * @param pageable pagination params
     * @return matching products
     */
    Page<Product> searchByName(String name, Pageable pageable);

    /**
     * Products with stock at or below the given threshold.
     * Used for inventory alerting.
     *
     * @param threshold stock level to alert on
     * @return list of low-stock products (unpaginated — typically small set)
     */
    List<Product> findLowStockProducts(int threshold);

    /**
     * Soft-delete a product by ID (sets active=false).
     *
     * @param id product ID to deactivate
     * @return number of rows affected (0 if ID not found)
     */
    int softDelete(Long id);
}
