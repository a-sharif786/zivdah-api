package com.zivdah.product.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("wishlists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistEntity {

    @Id
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createdAt;
}
