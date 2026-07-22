package com.zivdah.inventory.service;

import com.zivdah.inventory.dto.InventoryResponseDto;
import reactor.core.publisher.Mono;

public interface InventoryService {
    Mono<InventoryResponseDto> getInventoryByProductId(Long productId);
    Mono<InventoryResponseDto> addStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> reserveStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> releaseStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> confirmStock(Long productId, Integer quantity);
}
