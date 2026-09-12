package com.zivdah.payment.repository;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

// Plain value type for PaymentStatsRepository#dailyNetSeries — one row per calendar day, built
// by hand from the Row (see PaymentStatsRepository) rather than left to Spring Data's automatic
// interface-projection mapping — see PaymentTotalsProjection for why.
@Value
public class DailyNetProjection {
    LocalDate day;
    BigDecimal amount;
}
