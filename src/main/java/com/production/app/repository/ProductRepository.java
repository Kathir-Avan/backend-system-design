package com.production.app.repository;

import com.production.app.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * SPRING DATA JPA REPOSITORY — ProductRepository
 * ============================================================
 *
 * This interface is the JPA-based persistence layer.
 * Spring Data JPA auto-generates the implementation at startup —
 * no boilerplate SQL or connection management needed here.
 *
 * EXTENDS:
 *   JpaRepository<Product, Long>
 *     — provides save(), findById(), findAll(), delete(), count(), etc.
 *     — "Long" is the type of the @Id field in Product.
 *
 *   JpaSpecificationExecutor<Product>
 *     — enables dynamic, type-safe queries via the Criteria API.
 *     — used for complex filter scenarios (e.g., search by multiple
 *       optional fields) without writing raw JPQL.
 *
 * QUERY METHODS:
 *   Spring Data parses method names like findBySkuIgnoreCase() and
 *   generates the JPQL automatically. Naming convention:
 *   find[By][Field][Operator](params)
 *   e.g., findByPriceBetweenAndCategoryAndActiveTrue(min, max, cat)
 *
 * PRODUCTION NOTE:
 *   This repository is used by the JPA-mode DAO implementation.
 *   In Hibernate-native mode, the DAO uses SessionFactory directly
 *   (see HibernateProductDAO). The repository itself doesn't change —
 *   only the DAO implementation that wraps it changes per profile.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
                                           JpaSpecificationExecutor<Product> {

    // ============================================================
    // DERIVED QUERY METHODS (auto-implemented by Spring Data)
    // ============================================================

    /**
     * Find a product by its unique SKU code.
     * Returns Optional to force the caller to handle "not found" explicitly —
     * no NullPointerExceptions allowed in production code.
     */
    Optional<Product> findBySkuIgnoreCase(String sku);

    /**
     * Check existence before inserting a new SKU to enforce uniqueness
     * at the application layer (in addition to the DB UNIQUE constraint).
     * Two layers of protection = defense in depth.
     */
    boolean existsBySkuIgnoreCase(String sku);

    /**
     * Retrieve all active products in a given category.
     * Pagination is mandatory for list endpoints in production —
     * never return unbounded lists (SELECT * without LIMIT is a DoS risk).
     *
     * @param category  the product category to filter by
     * @param pageable  page number, page size, and sort order from the client
     * @return          a Page object with content + total element count
     */
    Page<Product> findByCategoryAndActiveTrue(String category, Pageable pageable);

    /**
     * Price range filter. BETWEEN is inclusive on both ends.
     * Pageable prevents returning 10,000 products at once.
     */
    Page<Product> findByPriceBetweenAndActiveTrue(BigDecimal minPrice,
                                                   BigDecimal maxPrice,
                                                   Pageable pageable);

    /**
     * Search by name — case-insensitive LIKE %keyword%.
     * %:name% adds SQL wildcards around the parameter.
     * nativeQuery=false means this is JPQL (portable across DBs).
     *
     * NOTE: LIKE queries are slow on large tables without full-text indexes.
     * Production systems use Elasticsearch/OpenSearch for text search.
     * This is acceptable for <= 100k rows.
     */
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "AND p.active = true")
    Page<Product> searchByName(@Param("name") String name, Pageable pageable);

    /**
     * Find all products with stock below the given threshold.
     * Used by inventory management systems to trigger restock alerts.
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= :threshold AND p.active = true")
    List<Product> findLowStockProducts(@Param("threshold") int threshold);

    /**
     * Soft-delete: sets active=false instead of physically deleting the row.
     *
     * @Modifying marks this as a DML (Data Modification Language) query.
     * clearAutomatically=true clears the EntityManager's first-level cache
     * after the update, ensuring subsequent reads reflect the change.
     *
     * @Transactional is required for @Modifying queries — the DAO/service
     * layer manages transactions, so we inherit it from there.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.active = false WHERE p.id = :id")
    int softDeleteById(@Param("id") Long id);

    /**
     * Get only active products (not soft-deleted).
     * Used for the default product listing endpoint.
     */
    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Native SQL query example — for when JPQL isn't expressive enough.
     * nativeQuery=true executes raw SQL against the underlying MySQL DB.
     *
     * Use sparingly: native queries are DB-specific and break portability.
     * When you must use them (e.g., window functions, JSON operations),
     * document the reason clearly.
     */
    @Query(value = "SELECT * FROM products WHERE category = :category " +
                   "ORDER BY price ASC LIMIT :limit",
           nativeQuery = true)
    List<Product> findCheapestByCategory(@Param("category") String category,
                                          @Param("limit") int limit);
}
