package com.production.app.dao.hibernate;

import com.production.app.dao.ProductDAO;
import com.production.app.model.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * NATIVE HIBERNATE DAO IMPLEMENTATION — HibernateProductDAO
 * ============================================================
 *
 * This implementation uses Hibernate's native Session API directly,
 * bypassing Spring Data JPA's repository abstraction.
 *
 * WHEN WOULD YOU USE NATIVE HIBERNATE IN PRODUCTION?
 *   - You need Hibernate-specific features not available in JPQL:
 *     multiLoad(), scrollable cursors, StatelessSession for bulk ops.
 *   - You need fine-grained cache control (2nd-level cache regions).
 *   - You're migrating a legacy Hibernate 4/5 project to Spring Boot.
 *   - You need dynamic queries built via Criteria API without JPQL strings.
 *
 * PROFILE-BASED ACTIVATION:
 *   @Profile("hibernate") — only loaded when profile = "hibernate".
 *   Switch to this mode:
 *     -Dspring.profiles.active=hibernate
 *   or in application.properties:
 *     spring.profiles.active=hibernate
 *
 * HOW IT WORKS:
 *   EntityManager is the JPA standard wrapper. unwrap(Session.class)
 *   gives access to the Hibernate Session underneath. Both are available
 *   since Spring Boot's JPA auto-configuration sets up Hibernate as the
 *   JPA provider. We use both to demonstrate JPA Criteria API (EntityManager)
 *   and Hibernate-native API (Session.createQuery with HQL).
 *
 * @PersistenceContext:
 *   Injects a proxy EntityManager that is scoped to the current
 *   transaction. Thread-safe — each transaction gets its own
 *   EntityManager instance via the proxy.
 */
@Repository
@Profile("hibernate")
@Slf4j
@Transactional   // All methods in this class participate in transactions
public class HibernateProductDAO implements ProductDAO {

    /**
     * @PersistenceContext is the standard JEE/Jakarta way to inject
     * the EntityManager (contrast with @Autowired which is Spring-specific).
     * Spring manages the lifecycle, so this is thread-safe.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Get the Hibernate Session from the EntityManager.
     * Session provides Hibernate-native operations like:
     *   - session.createQuery(HQL)
     *   - session.createNativeQuery(SQL)
     *   - session.byMultipleIds() for batch loading
     *   - session.enableFilter() for @Filter annotations
     */
    private Session getSession() {
        return entityManager.unwrap(Session.class);
    }

    /**
     * PERSIST using native Hibernate Session.
     *
     * session.persist(entity) is equivalent to entityManager.persist().
     * After the method returns (within the transaction), the entity
     * is in MANAGED state and has its id populated from the DB.
     */
    @Override
    public Product save(Product product) {
        log.debug("[HIBERNATE-DAO] Saving product with SKU: {}", product.getSku());
        Session session = getSession();
        if (product.getId() == null) {
            // New entity — INSERT
            session.persist(product);
            log.info("[HIBERNATE-DAO] Product persisted with id: {}", product.getId());
        } else {
            // Existing entity — UPDATE via merge
            Product merged = session.merge(product);
            log.info("[HIBERNATE-DAO] Product merged for id: {}", merged.getId());
            return merged;
        }
        return product;
    }

    /**
     * FIND BY ID using Hibernate Session.
     *
     * session.get() checks the first-level cache (EntityManager cache)
     * first, then hits the DB. Returns null (not Optional) natively —
     * we wrap it in Optional here to match our DAO interface contract.
     *
     * session.get() vs session.load():
     *   get()  — returns null if not found; hits DB immediately
     *   load() — returns a proxy; throws ObjectNotFoundException lazily
     *   Prefer get() for existence-uncertain lookups.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        log.debug("[HIBERNATE-DAO] Looking up product id: {}", id);
        Product product = getSession().get(Product.class, id);
        return Optional.ofNullable(product);
    }

    /**
     * FIND BY SKU using HQL (Hibernate Query Language).
     *
     * HQL is similar to JPQL but supports Hibernate extensions
     * like FETCH JOIN with WHERE clauses, @Filter application, etc.
     * The query is type-safe via createQuery(String, Class).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        log.debug("[HIBERNATE-DAO] Looking up product by SKU: {}", sku);
        Query<Product> query = getSession().createQuery(
            "FROM Product p WHERE LOWER(p.sku) = LOWER(:sku) AND p.active = true",
            Product.class
        );
        query.setParameter("sku", sku);
        // uniqueResultOptional() returns Optional.empty() if no result,
        // instead of throwing NoResultException like uniqueResult().
        return query.uniqueResultOptional();
    }

    /**
     * EXISTENCE CHECK using HQL COUNT query.
     * More efficient than fetching the full entity just to check existence.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        log.debug("[HIBERNATE-DAO] Checking SKU existence: {}", sku);
        Query<Long> query = getSession().createQuery(
            "SELECT COUNT(p) FROM Product p WHERE LOWER(p.sku) = LOWER(:sku)",
            Long.class
        );
        query.setParameter("sku", sku);
        Long count = query.uniqueResult();
        return count != null && count > 0;
    }

    /**
     * PAGINATED LIST using JPA Criteria API.
     *
     * The Criteria API builds queries programmatically (no string concatenation).
     * It is type-safe and refactoring-friendly — renaming "active" in the
     * entity will cause a compile error here, not a silent runtime failure.
     *
     * We use CriteriaBuilder from EntityManager (not Session) because the
     * JPA Criteria API is more standardized and better supported by IDEs.
     *
     * PAGINATION IMPLEMENTATION:
     *   JPA/Hibernate has no built-in Page<T> — Spring Data adds that.
     *   Here we manually implement pagination by:
     *     1. Running a COUNT query to get totalElements
     *     2. Running the data query with setFirstResult/setMaxResults
     *     3. Wrapping results in Spring's PageImpl<>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Product> findAllActive(Pageable pageable) {
        log.debug("[HIBERNATE-DAO] Fetching all active products, page {}", pageable.getPageNumber());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // === COUNT QUERY ===
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        countQuery.select(cb.count(countRoot))
                  .where(cb.isTrue(countRoot.get("active")));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        // === DATA QUERY ===
        CriteriaQuery<Product> dataQuery = cb.createQuery(Product.class);
        Root<Product> root = dataQuery.from(Product.class);
        dataQuery.select(root).where(cb.isTrue(root.get("active")));

        // Apply sort order from Pageable
        if (pageable.getSort().isSorted()) {
            List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                if (order.isAscending()) {
                    orders.add(cb.asc(root.get(order.getProperty())));
                } else {
                    orders.add(cb.desc(root.get(order.getProperty())));
                }
            });
            dataQuery.orderBy(orders);
        }

        TypedQuery<Product> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Product> results = typedQuery.getResultList();
        return new PageImpl<>(results, pageable, total);
    }

    /**
     * CATEGORY FILTER using HQL with pagination.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Product> findByCategory(String category, Pageable pageable) {
        log.debug("[HIBERNATE-DAO] Fetching products in category: {}", category);

        long total = getSession().createQuery(
            "SELECT COUNT(p) FROM Product p WHERE p.category = :cat AND p.active = true", Long.class)
            .setParameter("cat", category)
            .uniqueResult();

        List<Product> results = getSession().createQuery(
            "FROM Product p WHERE p.category = :cat AND p.active = true ORDER BY p.name ASC",
            Product.class)
            .setParameter("cat", category)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .list();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * PRICE RANGE FILTER using HQL.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice,
                                           Pageable pageable) {
        log.debug("[HIBERNATE-DAO] Fetching products in price range [{}, {}]", minPrice, maxPrice);

        long total = getSession().createQuery(
            "SELECT COUNT(p) FROM Product p WHERE p.price BETWEEN :min AND :max AND p.active = true",
            Long.class)
            .setParameter("min", minPrice)
            .setParameter("max", maxPrice)
            .uniqueResult();

        List<Product> results = getSession().createQuery(
            "FROM Product p WHERE p.price BETWEEN :min AND :max AND p.active = true ORDER BY p.price ASC",
            Product.class)
            .setParameter("min", minPrice)
            .setParameter("max", maxPrice)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .list();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * NAME SEARCH using HQL LIKE with case-insensitive comparison.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Product> searchByName(String name, Pageable pageable) {
        log.debug("[HIBERNATE-DAO] Searching products by name: '{}'", name);
        String pattern = "%" + name.toLowerCase() + "%";

        long total = getSession().createQuery(
            "SELECT COUNT(p) FROM Product p WHERE LOWER(p.name) LIKE :pattern AND p.active = true",
            Long.class)
            .setParameter("pattern", pattern)
            .uniqueResult();

        List<Product> results = getSession().createQuery(
            "FROM Product p WHERE LOWER(p.name) LIKE :pattern AND p.active = true ORDER BY p.name ASC",
            Product.class)
            .setParameter("pattern", pattern)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .list();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * LOW STOCK QUERY using HQL.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Product> findLowStockProducts(int threshold) {
        log.debug("[HIBERNATE-DAO] Finding low-stock products (threshold: {})", threshold);
        return getSession().createQuery(
            "FROM Product p WHERE p.stockQuantity <= :threshold AND p.active = true ORDER BY p.stockQuantity ASC",
            Product.class)
            .setParameter("threshold", threshold)
            .list();
    }

    /**
     * SOFT DELETE using HQL UPDATE.
     *
     * HQL UPDATE is a bulk operation — it bypasses the EntityManager
     * cache and executes directly against the DB. We use executeUpdate()
     * which returns the number of rows modified.
     *
     * Important: After this, any Product entities already loaded in the
     * EntityManager cache are stale. In JPA mode, Spring handles cache
     * clearing. Here, we do it manually with session.evict() if needed.
     */
    @Override
    public int softDelete(Long id) {
        log.info("[HIBERNATE-DAO] Soft-deleting product id: {}", id);
        int affected = getSession().createMutationQuery(
            "UPDATE Product p SET p.active = false WHERE p.id = :id")
            .setParameter("id", id)
            .executeUpdate();

        // Evict the entity from the L1 cache so subsequent reads reflect the change
        if (affected > 0) {
            Product cached = getSession().get(Product.class, id);
            if (cached != null) {
                getSession().evict(cached);
            }
        }

        log.info("[HIBERNATE-DAO] Soft-delete affected {} row(s)", affected);
        return affected;
    }
}
