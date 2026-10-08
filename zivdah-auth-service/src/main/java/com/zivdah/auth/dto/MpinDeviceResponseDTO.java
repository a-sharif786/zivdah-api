package com.zivdah.auth.dto;

import com.zivdah.auth.entity.MpinDevice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpinDeviceResponseDTO {

    private String deviceId;
    private String deviceName;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    public static MpinDeviceResponseDTO from(MpinDevice d) {
        return MpinDeviceResponseDTO.builder()
                .deviceId(d.getDeviceId())
                .deviceName(d.getDeviceName())
                .createdAt(d.getCreatedAt())
                .lastUsedAt(d.getLastUsedAt())
                .build();
    }
}
