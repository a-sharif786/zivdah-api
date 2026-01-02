package com.zivdah.product.dto;

import com.zivdah.product.enums.ProductCategory;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100)
    private String name;

    @NotNull(message = "Category is required")
    private ProductCategory category;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Discount price must be >= 0")
    private BigDecimal discountPrice;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private LocalDate expiryDate;

    private String description;

    private String imageUrl;

    private Boolean organic;

    private Boolean fav;

    private String brand;
}
