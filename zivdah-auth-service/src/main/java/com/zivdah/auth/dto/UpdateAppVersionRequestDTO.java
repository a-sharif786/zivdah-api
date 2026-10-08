package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Full replacement of a release's editable fields. Platform is fixed once released, and
// active is toggled separately via PATCH /{id}/status.
@Data
public class UpdateAppVersionRequestDTO {

    @NotBlank(message = "Version is required")
    @Pattern(regexp = "^\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}$", message = "Version must be MAJOR.MINOR.PATCH, e.g. 1.2.0")
    private String version;

    @NotBlank(message = "Store URL is required")
    @Pattern(regexp = "^https://\\S+$", message = "Store URL must be an https:// link")
    @Size(max = 512, message = "Store URL must be at most 512 characters")
    private String storeUrl;

    @NotNull(message = "forceUpdate is required")
    private Boolean forceUpdate;

    @Size(max = 2000, message = "Release notes must be at most 2000 characters")
    private String releaseNotes;
}
