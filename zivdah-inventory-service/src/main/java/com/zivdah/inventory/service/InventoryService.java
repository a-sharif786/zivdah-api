package com.zivdah.inventory.service;

import com.zivdah.inventory.dto.InventoryResponseDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InventoryService {
    Mono<InventoryResponseDto> getInventoryByProductId(Long productId);
    Mono<InventoryResponseDto> addStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> reserveStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> releaseStock(Long productId, Integer quantity);
    Mono<InventoryResponseDto> confirmStock(Long productId, Integer quantity);
    Flux<InventoryResponseDto> getAllInventory(Pageable pageable);
}
