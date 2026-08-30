package com.zivdah.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for the internal PUT /products/{id}/sync-stock endpoint — inventory-service calls
 * this after its own availableQuantity changes (add/reserve/release), to push the new value
 * here directly. This is the receiving side of the Product<->Inventory sync and must never
 * call back to inventory-service, or the two services would loop forever pushing updates at
 * each other.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncStockRequestDto {
    private Integer stockQuantity;
}
