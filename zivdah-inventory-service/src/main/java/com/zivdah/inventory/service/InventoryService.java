package com.zivdah.inventory.service;

import com.zivdah.inventory.dto.InventoryResponseDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InventoryService {
    Mono<InventoryResponseDto> getInventoryByProductId(Long productId);
    Mono<InventoryResponseDto> addStock(Long productId, Integer quantity);

    /**
     * Sets availableQuantity to an absolute value (not an increment, unlike addStock) —
     * used only by the internal sync-from-product-service path. Never call this from
     * anything that should also push the new value back to product-service; it's the
     * receiving side of that sync.
     */
    Mono<InventoryResponseDto> setAvailableQuantitySync(Long productId, Integer availableQuantity);
    Mono<InventoryResponseDto> reserveStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> releaseStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> confirmStock(Long productId, Integer quantity);
    Flux<InventoryResponseDto> getAllInventory(Pageable pageable);
}
