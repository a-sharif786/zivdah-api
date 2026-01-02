package com.zivdah.inventory.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponseDto {
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private LocalDateTime lastUpdated;
}