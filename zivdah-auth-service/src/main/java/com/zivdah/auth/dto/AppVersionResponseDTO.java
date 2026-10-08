package com.zivdah.auth.dto;

import com.zivdah.auth.entity.AppVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionResponseDTO {

    private Long id;
    private String platform;
    private String version;
    private String storeUrl;
    private boolean forceUpdate;
    private boolean active;
    private String releaseNotes;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AppVersionResponseDTO from(AppVersion v) {
        return AppVersionResponseDTO.builder()
                .id(v.getId())
                .platform(v.getPlatform().apiValue())
                .version(v.getVersion())
                .storeUrl(v.getStoreUrl())
                .forceUpdate(v.isForceUpdate())
                .active(v.isActive())
                .releaseNotes(v.getReleaseNotes())
                .releasedAt(v.getReleasedAt())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
