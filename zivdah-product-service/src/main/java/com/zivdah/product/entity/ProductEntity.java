package com.zivdah.product.entity;

import com.zivdah.product.enums.ProductCategory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {

    @Id
    private Long id;
    private String name;
    private ProductCategory category;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String unit;
    private Integer stockQuantity;
    private LocalDate expiryDate;
    private String description;
    private String imageUrl;
    private String imagePublicId;
    private String imageResourceType;
    private String imageFormat;
    private Long imageSizeBytes;
    private Boolean organic;
    private String brand;
    private Boolean fav;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean inStock;
    // Null = platform-owned (created by ADMIN). Set = owned by that VENDOR userId.
    private Long vendorId;
}
