package com.zivdah.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCheckResponseDTO {

    private String platform;
    private String currentVersion;
    private String latestVersion;
    private boolean updateRequired;
    private boolean forceUpdate;
    private String message;
    private String storeUrl;
}
