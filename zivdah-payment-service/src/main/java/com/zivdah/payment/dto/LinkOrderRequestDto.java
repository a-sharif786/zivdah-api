package com.zivdah.payment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkOrderRequestDto {
    private Long orderId;
}
