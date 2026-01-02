package com.zivdah.inventory.service;

import com.zivdah.inventory.dto.InventoryResponseDto;

public interface InventoryService {

    InventoryResponseDto getInventoryByProductId(Long productId);

    InventoryResponseDto addStock(Long productId, Integer quantity);

    InventoryResponseDto reserveStock(Long productId, Integer quantity);

    InventoryResponseDto releaseStock(Long productId, Integer quantity);

    InventoryResponseDto confirmStock(Long productId, Integer quantity);
}