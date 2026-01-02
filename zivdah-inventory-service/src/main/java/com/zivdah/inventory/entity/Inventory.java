package com.zivdah.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private Integer availableQuantity;

    @Column(nullable = false)
    private Integer reservedQuantity;

    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}
