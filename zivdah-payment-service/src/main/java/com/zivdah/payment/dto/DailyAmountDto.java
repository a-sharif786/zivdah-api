package com.zivdah.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAmountDto {
    private LocalDate date;
    private BigDecimal amount;
}
