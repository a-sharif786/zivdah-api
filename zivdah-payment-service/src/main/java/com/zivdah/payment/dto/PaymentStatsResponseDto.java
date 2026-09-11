package com.zivdah.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatsResponseDto {
    // Net of any refund: sum of (amount - refundAmount) over SUCCESS/REFUNDED payments,
    // unaffected by the from/to range below.
    private BigDecimal totalReceivedAllTime;

    // Net of any refund, scoped to payments whose paidAt falls within [from, to].
    private BigDecimal totalReceivedInRange;

    // Sum of refundAmount over all SUCCESS/REFUNDED payments, all-time.
    private BigDecimal totalRefundedAllTime;

    // Sum of refundAmount over payments whose paidAt falls within [from, to] — same time
    // basis as totalReceivedInRange, so a payment refunded later still nets out of the range
    // it was originally paid in.
    private BigDecimal totalRefundedInRange;

    // Daily buckets within the range (net of refunds), ascending by date, for a trend chart.
    private List<DailyAmountDto> series;
}
