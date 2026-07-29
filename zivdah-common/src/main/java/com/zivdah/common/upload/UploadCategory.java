package com.zivdah.common.upload;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Supported upload kinds shared across modules. Each carries its own allowed
 * content types, size ceiling, and the Cloudinary resource_type it maps to.
 */
public enum UploadCategory {

    IMAGE(
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif"),
            5L * 1024 * 1024,
            "image"
    ),
    VIDEO(
            Set.of("video/mp4", "video/quicktime", "video/x-matroska", "video/webm"),
            50L * 1024 * 1024,
            "video"
    ),
    DOCUMENT(
            Set.of("application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            10L * 1024 * 1024,
            "raw"
    );

    private final Set<String> allowedContentTypes;
    private final long maxSizeBytes;
    private final String cloudinaryResourceType;

    UploadCategory(Set<String> allowedContentTypes, long maxSizeBytes, String cloudinaryResourceType) {
        this.allowedContentTypes = allowedContentTypes;
        this.maxSizeBytes = maxSizeBytes;
        this.cloudinaryResourceType = cloudinaryResourceType;
    }

    public String getCloudinaryResourceType() {
        return cloudinaryResourceType;
    }

    public void validate(String contentType, long sizeBytes) {
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type for " + name() + ": " + contentType
                            + ". Allowed: " + allowedContentTypes);
        }
        if (sizeBytes > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File too large for " + name() + ": " + sizeBytes + " bytes"
                            + ". Max allowed: " + maxSizeBytes + " bytes");
        }
    }
}
