package com.zivdah.common.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {
    private Long orderId;
    private Long userId;
    private String status; // PAID or FAILED
}
