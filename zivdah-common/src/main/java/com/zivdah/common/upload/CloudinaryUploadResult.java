package com.zivdah.common.upload;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudinaryUploadResult {
    private String secureUrl;
    private String publicId;
    private String resourceType;
    private String format;
    private Long bytes;
    private String originalFilename;
}
