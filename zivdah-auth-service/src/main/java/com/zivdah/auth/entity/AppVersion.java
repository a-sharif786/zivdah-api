package com.zivdah.auth.entity;

import com.zivdah.auth.enums.AppPlatform;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// One row per released mobile app version. The "latest" version of a platform is the highest
// semantic version among its active rows — disabling a row rolls clients back to the next one.
@Table("app_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersion {

    @Id
    private Long id;
    private AppPlatform platform;
    private String version;
    private int versionMajor;
    private int versionMinor;
    private int versionPatch;
    private String storeUrl;
    private boolean forceUpdate;
    private boolean active;
    private String releaseNotes;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
