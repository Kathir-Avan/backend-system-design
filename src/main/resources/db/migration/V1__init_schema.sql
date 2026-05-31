-- ==============================================================
-- FLYWAY MIGRATION: V1__init_schema.sql
-- ==============================================================
--
-- This is the initial database schema for the production application.
--
-- FLYWAY NAMING CONVENTION:
--   V{version}__{description}.sql
--   V1__init_schema.sql        ← initial schema
--   V2__add_product_tags.sql   ← additive change
--   V3__add_users_table.sql    ← new table
--
-- HOW FLYWAY WORKS:
--   1. On app startup, Flyway checks the `flyway_schema_history` table.
--   2. Any migration file not yet applied is executed in version order.
--   3. The migration's checksum is stored — modifying applied migrations
--      causes a startup error (prevents accidental schema drift).
--
-- RULES FOR PRODUCTION MIGRATIONS:
--   1. NEVER modify a migration file after it has been applied to any
--      environment (dev, staging, prod). Create a new migration instead.
--   2. ALWAYS make migrations idempotent where possible (IF NOT EXISTS).
--   3. For large tables, use pt-online-schema-change or gh-ost instead
--      of direct ALTER TABLE (avoids table locks on production traffic).
--   4. Run EXPLAIN on all queries and verify indexes are used.
--
-- ==============================================================

-- ──────────────────────────────────────────────
-- PRODUCTS TABLE
-- ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS products (
    -- PRIMARY KEY
    -- AUTO_INCREMENT is the MySQL equivalent of JPA's GenerationType.IDENTITY.
    -- BIGINT UNSIGNED allows up to ~18.4 quintillion rows.
    id                BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,

    -- UNIQUE BUSINESS IDENTIFIER
    -- SKU (Stock Keeping Unit) is the human-readable product code.
    -- VARCHAR(50) with CHARACTER SET utf8mb4 supports international chars.
    -- UNIQUE constraint at DB level as a second line of defense
    -- (first: service layer check before insert).
    sku               VARCHAR(50)         NOT NULL,

    -- PRODUCT DETAILS
    name              VARCHAR(255)        NOT NULL,

    -- TEXT for descriptions up to 65,535 bytes (~64KB).
    -- Use MEDIUMTEXT (16MB) if products may have very long descriptions.
    description       TEXT                NULL,

    -- DECIMAL(10,2) stores values from -99999999.99 to 99999999.99.
    -- DECIMAL is exact (unlike FLOAT/DOUBLE) — mandatory for monetary values.
    price             DECIMAL(10, 2)      NOT NULL,

    -- INT UNSIGNED: stock can't be negative, max ~4.29 billion.
    stock_quantity    INT UNSIGNED        NOT NULL DEFAULT 0,

    category          VARCHAR(100)        NOT NULL,

    -- SOFT DELETE FLAG
    -- active=0 means deleted; queries filter by active=1.
    active            TINYINT(1)          NOT NULL DEFAULT 1,

    -- AUDIT TIMESTAMPS
    -- NOT NULL with DEFAULT CURRENT_TIMESTAMP ensures every row has a value
    -- even if inserted via raw SQL (bypassing the Spring layer).
    created_at        DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                          ON UPDATE CURRENT_TIMESTAMP(3),

    -- PRIMARY KEY CONSTRAINT
    PRIMARY KEY (id),

    -- UNIQUE CONSTRAINTS
    -- Named constraints are easier to identify in error messages and SHOW CREATE TABLE.
    CONSTRAINT uq_products_sku UNIQUE (sku)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Product catalog — soft-delete enabled via active flag';

-- ──────────────────────────────────────────────
-- INDEXES
-- ──────────────────────────────────────────────

-- Index on category alone: supports queries like "all products in Electronics".
-- Also serves as the leftmost prefix of the composite index below.
CREATE INDEX idx_products_category
    ON products (category);

-- Composite index (category, price):
-- Optimizes: WHERE category = 'Electronics' AND price BETWEEN 10 AND 100
-- MySQL uses index for both filtering AND range scanning in one pass.
CREATE INDEX idx_products_category_price
    ON products (category, price);

-- Index on active + id: used by the soft-delete pattern.
-- "SELECT * FROM products WHERE active = 1 ORDER BY id" uses this fully.
CREATE INDEX idx_products_active
    ON products (active, id);

-- Index on stock_quantity: used by low-stock queries.
-- WHERE stock_quantity <= 10 AND active = 1 — partial scan on this index.
CREATE INDEX idx_products_stock
    ON products (stock_quantity);

-- ──────────────────────────────────────────────
-- SEED DATA (optional — remove from prod migrations)
-- ──────────────────────────────────────────────
-- Sample data for development and testing.
-- In production, seed data goes in a separate R__ (repeatable) migration
-- or a dedicated data loading process.

INSERT INTO products (sku, name, description, price, stock_quantity, category, active)
VALUES
    ('LAPTOP-DELL-001',  'Dell XPS 15 9530',        'High-performance laptop with Intel Core i9, 32GB RAM, 1TB SSD', 1499.99, 15, 'Electronics', 1),
    ('LAPTOP-MAC-001',   'MacBook Pro 14 M3',        'Apple Silicon M3 Pro chip, 18GB unified memory, 512GB SSD',     1999.00, 8,  'Electronics', 1),
    ('PHONE-SAM-001',    'Samsung Galaxy S24 Ultra', '6.8-inch QHD+ Dynamic AMOLED, 200MP camera, 5000mAh battery',  1199.99, 25, 'Electronics', 1),
    ('BOOK-CLEAN-001',   'Clean Code',               'A Handbook of Agile Software Craftsmanship by Robert C. Martin', 35.99,  50, 'Books', 1),
    ('BOOK-DSA-001',     'Introduction to Algorithms', 'CLRS - 4th Edition, comprehensive algorithms textbook',         89.99,  30, 'Books', 1),
    ('CHAIR-ERGO-001',   'Herman Miller Aeron',      'Ergonomic office chair with PostureFit SL support',            1395.00, 5,  'Furniture', 1),
    ('HEADSET-SONY-001', 'Sony WH-1000XM5',          'Industry-leading noise canceling wireless headphones',           279.99, 20, 'Electronics', 1),
    ('MONITOR-LG-001',   'LG 27UK850-W 4K Monitor',  '27-inch UHD IPS display with USB-C connectivity',               499.99, 3,  'Electronics', 1),
    ('KEYBOARD-MECH-001','Keychron K2 V2',           'Wireless mechanical keyboard with RGB backlight, Brown switches', 79.99, 40, 'Accessories', 1),
    ('MOUSE-LOG-001',    'Logitech MX Master 3S',    'Advanced wireless mouse with 8K DPI tracking, silent clicks',    99.99, 35, 'Accessories', 1);
