package com.zivdah.common.upload;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Supported upload kinds shared across modules. Each carries its allowed content types (mapped to
 * the file extension the stored file gets), a size ceiling, and the resource type recorded next to
 * the stored file.
 */
public enum UploadCategory {

    IMAGE(
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif"),
            5L * 1024 * 1024,
            "image"
    ),
    VIDEO(
            Map.of("video/mp4", "mp4", "video/quicktime", "mov", "video/x-matroska", "mkv", "video/webm", "webm"),
            50L * 1024 * 1024,
            "video"
    ),
    DOCUMENT(
            Map.of("application/pdf", "pdf",
                    "application/msword", "doc",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            10L * 1024 * 1024,
            "document"
    );

    private final Map<String, String> extensionByContentType;
    private final long maxSizeBytes;
    private final String resourceType;

    UploadCategory(Map<String, String> extensionByContentType, long maxSizeBytes, String resourceType) {
        this.extensionByContentType = extensionByContentType;
        this.maxSizeBytes = maxSizeBytes;
        this.resourceType = resourceType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void validate(String contentType, long sizeBytes) {
        if (contentType == null || !extensionByContentType.containsKey(normalize(contentType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type for " + name() + ": " + contentType
                            + ". Allowed: " + extensionByContentType.keySet());
        }
        if (sizeBytes > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File too large for " + name() + ": " + sizeBytes + " bytes"
                            + ". Max allowed: " + maxSizeBytes + " bytes");
        }
    }

    /**
     * The client-declared content type is only a claim — files are served straight from disk by
     * nginx on the storefront domain, so the bytes must really be what the extension says (stops
     * e.g. an HTML/SVG page uploaded as "image/png").
     */
    public void verifyContent(String contentType, byte[] bytes) {
        if (!FileSignature.matches(normalize(contentType), bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File content does not match its declared type: " + contentType);
        }
    }

    /** Extension is always derived from the (validated) content type, never the client filename. */
    public String extensionFor(String contentType) {
        return extensionByContentType.get(normalize(contentType));
    }

    private static String normalize(String contentType) {
        // "image/jpeg;charset=..." -> "image/jpeg"
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private enum FileSignature {
        JPEG("image/jpeg", 0, 0xFF, 0xD8, 0xFF),
        PNG("image/png", 0, 0x89, 'P', 'N', 'G'),
        GIF("image/gif", 0, 'G', 'I', 'F', '8'),
        WEBP("image/webp", 8, 'W', 'E', 'B', 'P'),
        MP4("video/mp4", 4, 'f', 't', 'y', 'p'),
        MOV("video/quicktime", 4, 'f', 't', 'y', 'p'),
        MKV("video/x-matroska", 0, 0x1A, 0x45, 0xDF, 0xA3),
        WEBM("video/webm", 0, 0x1A, 0x45, 0xDF, 0xA3),
        PDF("application/pdf", 0, '%', 'P', 'D', 'F'),
        DOC("application/msword", 0, 0xD0, 0xCF, 0x11, 0xE0),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", 0, 'P', 'K', 0x03, 0x04);

        private final String contentType;
        private final int offset;
        private final int[] magic;

        FileSignature(String contentType, int offset, int... magic) {
            this.contentType = contentType;
            this.offset = offset;
            this.magic = magic;
        }

        static boolean matches(String contentType, byte[] bytes) {
            return Arrays.stream(values())
                    .filter(s -> s.contentType.equals(contentType))
                    .anyMatch(s -> s.matchesBytes(bytes));
        }

        private boolean matchesBytes(byte[] bytes) {
            if (bytes.length < offset + magic.length) {
                return false;
            }
            for (int i = 0; i < magic.length; i++) {
                if ((bytes[offset + i] & 0xFF) != magic[i]) {
                    return false;
                }
            }
            // WEBP is RIFF....WEBP
            return this != WEBP || (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F');
        }
    }
}
