package com.zivdah.chat.dto;

import com.zivdah.chat.client.dto.DeliverySummaryDto;
import com.zivdah.chat.client.dto.OrderSummaryDto;
import com.zivdah.chat.client.dto.PaymentSummaryDto;
import lombok.*;

import java.util.List;

// Section 10/12's agent info panel: Customer / Order ID / Order Amount / Payment Status /
// Order Status / Delivery Status / Vendor / Delivery Boy — aggregated from order/payment/
// delivery/auth-service, each leg best-effort (a down dependency blanks only its own field(s),
// never the whole panel — see OrderContextServiceImpl).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderContextResponseDto {
    private Long orderId;
    private OrderSummaryDto order;
    private List<PaymentSummaryDto> payments;
    private List<DeliverySummaryDto> deliveries;
    private String vendorName;
    private String deliveryBoyName;
}
