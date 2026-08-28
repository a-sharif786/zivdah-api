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
    // Sum of all SUCCESS payments' amounts, unaffected by the from/to range below.
    private BigDecimal totalReceivedAllTime;

    // Sum of SUCCESS payments' amounts within the requested [from, to] range (by paidAt).
    private BigDecimal totalReceivedInRange;

    // Daily buckets within the range, ascending by date, for a trend chart.
    private List<DailyAmountDto> series;
}
