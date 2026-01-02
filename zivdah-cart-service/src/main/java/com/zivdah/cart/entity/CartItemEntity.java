package com.zivdah.cart.entity;

import com.zivdah.cart.enums.CartItemStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user who owns this cart item */
    @Column(nullable = false)
    private Long userId;

    /** ID of the product */
    @Column(nullable = false)
    private Long productId;

    /** Quantity of the product */
    @Column(nullable = false)
    private Integer quantity;

    /** Unit price of the product at the time it was added */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Total price for this item (quantity * price) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /** Optional variant / SKU ID */
    private String sku;

    /** Cart item status, e.g., ACTIVE, SAVED, REMOVED */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CartItemStatus status;

    /** When this item was added to the cart */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Last updated timestamp */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Soft delete flag */
    @Column(nullable = false)
    private Boolean deleted = false;

    /** Automatically update timestamps */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        subtotal = price.multiply(BigDecimal.valueOf(quantity));
    }

}
