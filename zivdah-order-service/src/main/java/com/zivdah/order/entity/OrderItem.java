package com.zivdah.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

// R2DBC: orderId FK replaces the @ManyToOne Order reference
@Table("order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    private Long id;

    private Long orderId;

    private Long productId;

    // Snapshot details
    private String productName;

    private String productSku;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;

    // Item tax
    private BigDecimal gstAmount;

    private BigDecimal totalAmount;
}
