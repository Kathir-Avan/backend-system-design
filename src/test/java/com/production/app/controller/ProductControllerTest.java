package com.production.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.production.app.dto.ProductRequestDTO;
import com.production.app.dto.ProductResponseDTO;
import com.production.app.exception.ResourceNotFoundException;
import com.production.app.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * CONTROLLER SLICE TEST — ProductControllerTest
 * ============================================================
 *
 * @WebMvcTest: Loads ONLY the web layer (controllers, filters,
 * exception handlers). Does NOT load the full Spring context,
 * service beans, or database connections. Very fast.
 *
 * MockMvc: Simulates HTTP requests without a real server.
 * Allows testing routing, serialization, status codes, and
 * validation behavior end-to-end through the web layer.
 *
 * @MockBean: Creates a mock of ProductService and adds it to
 * the Spring context. The controller gets this mock injected.
 *
 * WHAT WE TEST HERE:
 *   - HTTP routing (@RequestMapping, @GetMapping, etc.)
 *   - Request body deserialization (JSON → DTO)
 *   - Bean Validation (@Valid triggers 400 errors)
 *   - Response serialization (DTO → JSON)
 *   - HTTP status codes (200, 201, 400, 404, 409)
 *   - Error response format from GlobalExceptionHandler
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController Integration Tests")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;  // Jackson for request body serialization

    @MockBean
    private ProductService productService;

    private ProductRequestDTO validRequest;
    private ProductResponseDTO mockResponse;

    @BeforeEach
    void setUp() {
        validRequest = ProductRequestDTO.builder()
                .sku("LAPTOP-001")
                .name("Test Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category("Electronics")
                .build();

        mockResponse = ProductResponseDTO.builder()
                .id(1L)
                .sku("LAPTOP-001")
                .name("Test Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .inStock(true)
                .active(true)
                .category("Electronics")
                .build();
    }

    // ============================================================
    // POST /api/v1/products
    // ============================================================

    @Test
    @DisplayName("POST /products — valid request returns 201 with product")
    void createProduct_validRequest_returns201() throws Exception {
        given(productService.createProduct(any(ProductRequestDTO.class)))
                .willReturn(mockResponse);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.sku").value("LAPTOP-001"))
                .andExpect(jsonPath("$.data.inStock").value(true));
    }

    @Test
    @DisplayName("POST /products — missing required field returns 400 with validation errors")
    void createProduct_missingName_returns400() throws Exception {
        // Remove required name field
        validRequest.setName(null);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.statusCode").value(400))
                // Validation error map should contain the "name" field error
                .andExpect(jsonPath("$.data.name").exists());
    }

    @Test
    @DisplayName("POST /products — negative price returns 400")
    void createProduct_negativePrice_returns400() throws Exception {
        validRequest.setPrice(new BigDecimal("-10.00"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.price").exists());
    }

    // ============================================================
    // GET /api/v1/products/{id}
    // ============================================================

    @Test
    @DisplayName("GET /products/{id} — found returns 200 with product")
    void getProductById_found_returns200() throws Exception {
        given(productService.getProductById(1L)).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.sku").value("LAPTOP-001"));
    }

    @Test
    @DisplayName("GET /products/{id} — not found returns 404")
    void getProductById_notFound_returns404() throws Exception {
        given(productService.getProductById(999L))
                .willThrow(new ResourceNotFoundException("Product", "id", 999L));

        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("GET /products/{id} — invalid ID type returns 400")
    void getProductById_invalidIdType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/abc"))  // "abc" cannot be parsed as Long
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ============================================================
    // DELETE /api/v1/products/{id}
    // ============================================================

    @Test
    @DisplayName("DELETE /products/{id} — success returns 204")
    void deleteProduct_found_returns204() throws Exception {
        willDoNothing().given(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /products/{id} — not found returns 404")
    void deleteProduct_notFound_returns404() throws Exception {
        willThrow(new ResourceNotFoundException("Product", "id", 999L))
                .given(productService).deleteProduct(999L);

        mockMvc.perform(delete("/api/v1/products/999"))
                .andExpect(status().isNotFound());
    }
}
