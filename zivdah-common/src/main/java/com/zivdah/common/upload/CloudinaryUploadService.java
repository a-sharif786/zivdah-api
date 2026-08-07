package com.zivdah.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Map;

/**
 * Reusable Cloudinary upload/delete logic, meant to be shared by any module
 * that needs to store images, videos, or documents. Each consuming service
 * wires its own {@link Cloudinary} bean (cloud name/api key/secret come from
 * that service's own config) and constructs this on top of it.
 */
@Slf4j
@RequiredArgsConstructor
public class CloudinaryUploadService {

    private final Cloudinary cloudinary;

    public Mono<CloudinaryUploadResult> upload(FilePart filePart, UploadCategory category, String folder) {
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : null;

        return DataBufferUtils.join(filePart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(bytes -> {
                    category.validate(contentType, bytes.length);
                    return Mono.fromCallable(() -> doUpload(bytes, filePart.filename(), category, folder))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnSuccess(r -> log.info("Uploaded {} to Cloudinary: {}", filePart.filename(), r.getPublicId()));
    }

    public Mono<Void> delete(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    try {
                        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
                    } catch (IOException e) {
                        throw new UploadFailedException("Failed to delete Cloudinary asset: " + publicId, e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("Deleted Cloudinary asset: {}", publicId))
                .then();
    }

    @SuppressWarnings("unchecked")
    private CloudinaryUploadResult doUpload(byte[] bytes, String filename, UploadCategory category, String folder) {
        try {
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", category.getCloudinaryResourceType(),
                    "use_filename", true,
                    "unique_filename", true
            );
            Map<String, Object> result = cloudinary.uploader().upload(bytes, options);
            return CloudinaryUploadResult.builder()
                    .secureUrl((String) result.get("secure_url"))
                    .publicId((String) result.get("public_id"))
                    .resourceType((String) result.get("resource_type"))
                    .format((String) result.get("format"))
                    .bytes(result.get("bytes") != null ? Long.valueOf(result.get("bytes").toString()) : null)
                    .originalFilename(filename)
                    .build();
        } catch (IOException e) {
            throw new UploadFailedException("Failed to upload " + filename + " to Cloudinary", e);
        }
    }
}
