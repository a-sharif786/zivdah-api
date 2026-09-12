package com.zivdah.order.dto;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequestDto {
    private OrderStatus status;

    // Only ever populated on the PAID transition (see payment-service's OrderServiceClient /
    // PaymentServiceImpl#markPaymentSuccess) so updatePaymentStatus() can hand them straight to
    // InvoiceService without a second round-trip back into payment-service to read them. Null
    // on every other transition (CANCELLED/REFUNDED).
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime paidAt;
}
