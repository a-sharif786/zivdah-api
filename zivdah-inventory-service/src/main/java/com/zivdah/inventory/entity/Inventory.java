package com.zivdah.inventory.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    private Long id;
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private LocalDateTime lastUpdated;
}
