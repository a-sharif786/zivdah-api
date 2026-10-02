package com.zivdah.common.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded files on the local filesystem under one storage root (e.g. PRODUCT_STORAGE_PATH
 * = /var/www/zivdah-media/products/), served publicly by nginx under publicBaseUrl — same model as
 * order-service's InvoiceStorageService. Each consuming service constructs one instance per storage
 * root (see its MediaStorageConfig).
 *
 * <p>Stored names are always {@code <uuid>.<ext>} (ext from the validated content type, never the
 * client filename), optionally inside one sub-directory, and every resolved path is re-checked to
 * stay inside the storage root.
 */
@Slf4j
public class LocalFileStorageService {

    private static final Pattern SAFE_SUB_DIRECTORY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    // Only keys this class generated are ever deleted — anything else (e.g. a legacy Cloudinary
    // public id still stored on an old row) is ignored rather than resolved against the disk.
    private static final Pattern OWN_STORAGE_KEY = Pattern.compile(
            "^(?:[A-Za-z0-9_-]{1,64}/)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{2,5}$");

    private final Path storageRoot;
    private final String publicBaseUrl;

    public LocalFileStorageService(String storagePath, String publicBaseUrl) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    public Mono<StoredFile> store(FilePart filePart, UploadCategory category) {
        return store(filePart, category, null);
    }

    public Mono<StoredFile> store(FilePart filePart, UploadCategory category, String subDirectory) {
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
                    category.verifyContent(contentType, bytes);
                    return Mono.fromCallable(() -> write(bytes, filePart.filename(), category, contentType, subDirectory))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnSuccess(f -> log.info("Stored upload {}", f.getStorageKey()));
    }

    public Mono<Void> delete(String storageKey) {
        if (storageKey == null || !OWN_STORAGE_KEY.matcher(storageKey).matches()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> Files.deleteIfExists(resolve(storageKey)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(deleted -> log.info("Deleted stored file {} (existed: {})", storageKey, deleted))
                // The DB row is already updated by the time we get here; a leftover orphan file
                // must not fail the user's request.
                .onErrorResume(e -> {
                    log.warn("Could not delete stored file {}: {}", storageKey, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private StoredFile write(byte[] bytes, String originalFilename, UploadCategory category,
                             String contentType, String subDirectory) {
        if (subDirectory != null && !SAFE_SUB_DIRECTORY.matcher(subDirectory).matches()) {
            throw new IllegalArgumentException("Invalid storage sub-directory: " + subDirectory);
        }
        String extension = category.extensionFor(contentType);
        String fileName = UUID.randomUUID() + "." + extension;
        String storageKey = subDirectory != null ? subDirectory + "/" + fileName : fileName;
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UploadFailedException("Failed to store " + originalFilename, e);
        }
        return StoredFile.builder()
                .url(publicBaseUrl + "/" + storageKey)
                .storageKey(storageKey)
                .resourceType(category.getResourceType())
                .format(extension)
                .bytes((long) bytes.length)
                .originalFilename(originalFilename)
                .build();
    }

    private Path resolve(String storageKey) {
        Path target = storageRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new UploadFailedException("Resolved path escapes the storage directory", null);
        }
        return target;
    }
}
