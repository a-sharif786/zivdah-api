package com.zivdah.common.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequestDto {
    private Long userId;
    private List<OrderItemDto> items;
}
