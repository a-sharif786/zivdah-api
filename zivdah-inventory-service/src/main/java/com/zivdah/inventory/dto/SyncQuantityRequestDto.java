package com.zivdah.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for the internal PUT /inventory/{productId}/sync-quantity endpoint — product-service
 * calls this after its own stockQuantity changes, to push the new value here directly (a
 * "set", not the "add on top" semantics of AddStockRequestDto/addStock). This is the
 * receiving side of the Product<->Inventory sync and must never call back to
 * product-service, or the two services would loop forever pushing updates at each other.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncQuantityRequestDto {
    private Integer availableQuantity;
}
