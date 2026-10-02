package com.zivdah.common.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Result of {@link LocalFileStorageService#store}. */
@Getter
@Builder
@AllArgsConstructor
public class StoredFile {
    /** Public URL the file is served at (publicBaseUrl + "/" + storageKey). */
    private String url;
    /** Path relative to the storage root, e.g. "3f2a...c1.jpg" or "42/3f2a...c1.pdf" — pass to delete(). */
    private String storageKey;
    private String resourceType;
    private String format;
    private Long bytes;
    private String originalFilename;
}
