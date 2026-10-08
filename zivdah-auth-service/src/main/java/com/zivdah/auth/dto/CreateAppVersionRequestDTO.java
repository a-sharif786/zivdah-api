package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAppVersionRequestDTO {

    @NotBlank(message = "Platform is required")
    @Pattern(regexp = "(?i)android|ios", message = "Platform must be android or ios")
    private String platform;

    @NotBlank(message = "Version is required")
    @Pattern(regexp = "^\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}$", message = "Version must be MAJOR.MINOR.PATCH, e.g. 1.2.0")
    private String version;

    @NotBlank(message = "Store URL is required")
    @Pattern(regexp = "^https://\\S+$", message = "Store URL must be an https:// link")
    @Size(max = 512, message = "Store URL must be at most 512 characters")
    private String storeUrl;

    private boolean forceUpdate = false;

    // Release immediately by default; send false to stage a version without exposing it yet.
    private boolean active = true;

    @Size(max = 2000, message = "Release notes must be at most 2000 characters")
    private String releaseNotes;
}
