package com.zivdah.payment.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {
    private Long orderId;
    private String status; // PAID or FAILED
}