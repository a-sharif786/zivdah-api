package com.zivdah.product.dto;

import com.zivdah.product.enums.ProductCategory;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private ProductCategory category;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String unit;
    private Integer stockQuantity;
    private Boolean inStock;
    private LocalDate expiryDate;
    private String description;
    private String imageUrl;
    private Boolean organic;
    private String brand;
    private Boolean fav;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
