package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySeriesPointDto {
    private LocalDate date;
    private long botCount;
    private long humanCount;
}
