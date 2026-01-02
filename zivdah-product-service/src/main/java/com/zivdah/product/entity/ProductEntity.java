package com.zivdah.product.entity;

import com.zivdah.product.enums.ProductCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_product_name", columnList = "name"),
                @Index(name = "idx_product_category", columnList = "category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {

    /* =======================
       PRIMARY KEY
       ======================= */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* =======================
       BASIC INFO
       ======================= */
    @NotBlank
    @Size(min = 2, max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductCategory category;

    /* =======================
       PRICING
       ======================= */
    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @PositiveOrZero
    @Column(precision = 10, scale = 2)
    private BigDecimal discountPrice;

    /* =======================
       MEASUREMENT
       ======================= */
    @NotBlank
    @Column(nullable = false, length = 20)
    private String unit; // kg, litre, packet

    /* =======================
       INVENTORY
       ======================= */
    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stockQuantity;

//    @Column(nullable = false)
//    private Boolean inStock;

    /* =======================
       PRODUCT DETAILS
       ======================= */
    private LocalDate expiryDate; // milk, pulses

    @Column(length = 300)
    private String description;

    @Column(length = 255)
    private String imageUrl;

    /* =======================
       PRODUCT ATTRIBUTES
       ======================= */
    private Boolean organic;

    @Column(length = 100)
    private String brand;

    /* =======================
       AUDIT FIELDS
       ======================= */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;



    @Column(nullable = false)
    private Boolean fav;

    @Column(nullable = false)
    private Boolean inStock;

    @PrePersist
    @PreUpdate
    private void prePersistAndUpdate() {

        if (fav == null) {
            fav = false;
        }

        this.inStock = stockQuantity != null && stockQuantity > 0;
    }


}
