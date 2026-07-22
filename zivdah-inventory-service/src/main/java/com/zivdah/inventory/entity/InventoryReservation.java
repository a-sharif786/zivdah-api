package com.zivdah.inventory.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String status; // RESERVED, CONFIRMED, RELEASED
}
