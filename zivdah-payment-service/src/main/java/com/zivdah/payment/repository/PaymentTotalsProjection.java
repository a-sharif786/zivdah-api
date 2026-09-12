package com.zivdah.payment.repository;

import lombok.Value;

import java.math.BigDecimal;

// Plain value type for PaymentStatsRepository's sumTotalsAllTime/sumTotalsInRange — built by
// hand from the Row (see PaymentStatsRepository) rather than left to Spring Data's automatic
// interface-projection mapping, which was observed handing back null gross/refunded here even
// though the identical SQL returns correct non-null totals run directly against Postgres.
@Value
public class PaymentTotalsProjection {
    BigDecimal gross;
    BigDecimal refunded;
}
