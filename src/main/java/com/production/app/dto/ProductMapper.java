package com.production.app.dto;

import com.production.app.model.Product;
import org.mapstruct.*;

import java.util.List;

/**
 * ============================================================
 * DTO MAPPER — ProductMapper (MapStruct)
 * ============================================================
 *
 * MapStruct generates the implementation of this interface at
 * compile time. The generated class is a Spring-managed bean
 * (due to componentModel = "spring" in pom.xml compiler args).
 *
 * WHY MAPSTRUCT OVER MODELMAPPER?
 *   ModelMapper uses reflection at runtime — expensive at scale.
 *   MapStruct generates plain getter/setter calls — same speed
 *   as hand-written code. Critical at Amazon/Netflix where a
 *   single service may perform millions of mappings per minute.
 *
 * ANNOTATIONS:
 *   @Mapper            — marks interface for MapStruct processing
 *   componentModel     — makes the generated impl a Spring @Component
 *   nullValuePropertyMappingStrategy — on PARTIAL UPDATE (PATCH),
 *     skip null source fields so they don't overwrite existing values.
 *
 * MAPPING RULES:
 *   MapStruct auto-maps fields with the same name and compatible types.
 *   Custom mappings (different names or types) use @Mapping.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    // UNMAPPED_TARGET_POLICY: WARN during build if a target field is not mapped.
    // Change to ERROR in strict projects to prevent silent data loss.
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface ProductMapper {

    /**
     * REQUEST DTO → ENTITY
     *
     * Maps incoming API request data to the domain entity.
     * Fields like id, createdAt, updatedAt are NOT in the DTO,
     * so they are ignored here — JPA/Hibernate sets them.
     *
     * @Mapping(target="active", constant="true") sets a literal
     * constant value on the target without any source field.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductRequestDTO requestDTO);

    /**
     * ENTITY → RESPONSE DTO
     *
     * Maps the domain entity to the API response object.
     * Adds the computed "inStock" field using an @AfterMapping hook
     * (see afterToResponseDTO below).
     *
     * @Mapping(ignore=true) on inStock tells MapStruct to skip
     * auto-mapping for that field — we populate it manually.
     */
    @Mapping(target = "inStock", ignore = true)
    ProductResponseDTO toResponseDTO(Product product);

    /**
     * After the main mapping, compute the derived "inStock" field.
     * @AfterMapping with @MappingTarget injects the already-mapped
     * target object so we can mutate it post-mapping.
     */
    @AfterMapping
    default void afterToResponseDTO(Product product, @MappingTarget ProductResponseDTO dto) {
        // A product is "in stock" if it has at least 1 unit available
        dto.setInStock(product.getStockQuantity() != null && product.getStockQuantity() > 0);
    }

    /**
     * LIST MAPPING — MapStruct auto-generates this by delegating to toResponseDTO().
     * One-liner that replaces a full forEach loop in the service layer.
     */
    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    /**
     * PARTIAL UPDATE: Merge request DTO fields INTO an existing entity.
     *
     * Used for PUT/PATCH operations. Only non-null fields from the DTO
     * are applied (due to NullValuePropertyMappingStrategy.IGNORE).
     * The entity's id, audit fields, and active flag are preserved.
     *
     * @MappingTarget tells MapStruct to update the existing object
     * instead of creating a new one.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(ProductRequestDTO requestDTO, @MappingTarget Product product);
}
