package com.zivdah.common.event;

import lombok.*;

/**
 * Published by zivdah-product-service right after a product (admin- or vendor-created) is
 * saved, so zivdah-inventory-service can seed an initial inventory row for it — without this,
 * a brand-new product has no inventory record at all until someone manually calls
 * POST /inventory/add, and the first order placed for it fails checkout with
 * "Inventory not found for product X".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreatedEvent {
    private Long productId;
    private Integer initialStockQuantity;
}
