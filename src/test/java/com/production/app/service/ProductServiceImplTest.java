package com.production.app.service;

import com.production.app.dao.ProductDAO;
import com.production.app.dto.ProductMapper;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.exception.DuplicateResourceException;
import com.production.app.exception.ResourceNotFoundException;
import com.production.app.model.Product;
import com.production.app.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * ============================================================
 * UNIT TEST — ProductServiceImplTest
 * ============================================================
 *
 * UNIT TESTS (this file):
 *   - Test business logic IN ISOLATION (no Spring context, no DB).
 *   - Fast: runs in milliseconds.
 *   - Uses Mockito to replace real dependencies with fakes.
 *   - Should cover 100% of business rules and edge cases.
 *
 * @ExtendWith(MockitoExtension.class):
 *   Integrates Mockito with JUnit 5. Initializes @Mock and @InjectMocks
 *   annotations automatically before each test.
 *
 * @Mock: Creates a Mockito mock (fake) of the dependency.
 * @InjectMocks: Creates the class under test and injects mocks into it.
 *
 * BDD STYLE (Given-When-Then):
 *   given(...)  — set up mock behavior (preconditions)
 *   when(...)   — invoke the method under test (action)
 *   then(...)   — verify results and mock interactions (assertions)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductDAO productDAO;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    // ── Test Fixtures ──────────────────────────────────────────

    private ProductRequestDTO validRequest;
    private Product savedProduct;
    private ProductResponseDTO expectedResponse;

    @BeforeEach
    void setUp() {
        // Build test data — shared across all test methods.
        // @BeforeEach re-runs this before EACH test for isolation.

        validRequest = ProductRequestDTO.builder()
                .sku("LAPTOP-001")
                .name("Test Laptop")
                .description("A test laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category("Electronics")
                .build();

        savedProduct = Product.builder()
                .id(1L)
                .sku("LAPTOP-001")
                .name("Test Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category("Electronics")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        expectedResponse = ProductResponseDTO.builder()
                .id(1L)
                .sku("LAPTOP-001")
                .name("Test Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .inStock(true)
                .category("Electronics")
                .active(true)
                .build();
    }

    // ============================================================
    // CREATE TESTS
    // ============================================================

    @Test
    @DisplayName("createProduct: success — valid request saves and returns DTO")
    void createProduct_givenValidRequest_returnsCreatedProductDTO() {
        // GIVEN
        given(productDAO.existsBySku("LAPTOP-001")).willReturn(false);
        given(productMapper.toEntity(any(ProductRequestDTO.class))).willReturn(savedProduct);
        given(productDAO.save(any(Product.class))).willReturn(savedProduct);
        given(productMapper.toResponseDTO(any(Product.class))).willReturn(expectedResponse);

        // WHEN
        ProductResponseDTO result = productService.createProduct(validRequest);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSku()).isEqualTo("LAPTOP-001");
        assertThat(result.getInStock()).isTrue();

        // Verify the DAO was called exactly once
        then(productDAO).should(times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: duplicate SKU — throws DuplicateResourceException")
    void createProduct_whenSkuAlreadyExists_throwsDuplicateResourceException() {
        // GIVEN: SKU already exists in the DB
        given(productDAO.existsBySku(anyString())).willReturn(true);

        // WHEN + THEN: expect the specific exception
        assertThatThrownBy(() -> productService.createProduct(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("LAPTOP-001");

        // VERIFY: save() should NEVER be called when SKU is duplicate
        then(productDAO).should(never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: SKU is normalized to uppercase")
    void createProduct_skuIsNormalizedToUppercase() {
        // GIVEN: SKU with lowercase letters
        validRequest.setSku("laptop-001");
        given(productDAO.existsBySku("LAPTOP-001")).willReturn(false);
        given(productMapper.toEntity(any())).willReturn(savedProduct);
        given(productDAO.save(any())).willReturn(savedProduct);
        given(productMapper.toResponseDTO(any())).willReturn(expectedResponse);

        // WHEN
        productService.createProduct(validRequest);

        // THEN: verify the DTO had its SKU uppercased before the DAO was checked
        assertThat(validRequest.getSku()).isEqualTo("LAPTOP-001");
    }

    // ============================================================
    // READ TESTS
    // ============================================================

    @Test
    @DisplayName("getProductById: found — returns DTO")
    void getProductById_whenProductExists_returnsDTO() {
        given(productDAO.findById(1L)).willReturn(Optional.of(savedProduct));
        given(productMapper.toResponseDTO(savedProduct)).willReturn(expectedResponse);

        ProductResponseDTO result = productService.getProductById(1L);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("getProductById: not found — throws ResourceNotFoundException")
    void getProductById_whenProductNotFound_throwsResourceNotFoundException() {
        given(productDAO.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getProductById: soft-deleted product — throws ResourceNotFoundException")
    void getProductById_whenProductSoftDeleted_throwsResourceNotFoundException() {
        // Soft-deleted product: active=false
        savedProduct.setActive(false);
        given(productDAO.findById(1L)).willReturn(Optional.of(savedProduct));

        // Soft-deleted products should be treated as "not found"
        assertThatThrownBy(() -> productService.getProductById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // DELETE TESTS
    // ============================================================

    @Test
    @DisplayName("deleteProduct: found — soft-deletes successfully")
    void deleteProduct_whenProductExists_softDeletesSuccessfully() {
        given(productDAO.softDelete(1L)).willReturn(1);

        assertThatCode(() -> productService.deleteProduct(1L))
                .doesNotThrowAnyException();

        then(productDAO).should(times(1)).softDelete(1L);
    }

    @Test
    @DisplayName("deleteProduct: not found — throws ResourceNotFoundException")
    void deleteProduct_whenProductNotFound_throwsResourceNotFoundException() {
        given(productDAO.softDelete(999L)).willReturn(0);  // 0 rows affected = not found

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // BUSINESS RULE TESTS
    // ============================================================

    @Test
    @DisplayName("getProductsByPriceRange: minPrice > maxPrice — throws IllegalArgumentException")
    void getProductsByPriceRange_whenMinGreaterThanMax_throwsIllegalArgumentException() {
        BigDecimal min = new BigDecimal("100.00");
        BigDecimal max = new BigDecimal("50.00");

        assertThatThrownBy(() -> productService.getProductsByPriceRange(min, max, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    @DisplayName("searchProducts: keyword too short — throws IllegalArgumentException")
    void searchProducts_whenKeywordTooShort_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> productService.searchProducts("a", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 characters");
    }
}
