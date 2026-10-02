package com.zivdah.common.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    @TempDir
    Path root;

    private LocalFileStorageService service() {
        return new LocalFileStorageService(root.toString(), "https://example.com/media/products/");
    }

    @Test
    void storesUnderUuidNameWithExtensionFromContentType() throws Exception {
        StoredFile stored = service().store(part("../../evil.html", "image/png", PNG), UploadCategory.IMAGE).block();

        assertThat(stored.getStorageKey()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.getUrl()).isEqualTo("https://example.com/media/products/" + stored.getStorageKey());
        assertThat(stored.getResourceType()).isEqualTo("image");
        assertThat(stored.getFormat()).isEqualTo("png");
        assertThat(stored.getBytes()).isEqualTo(PNG.length);
        assertThat(Files.readAllBytes(root.resolve(stored.getStorageKey()))).isEqualTo(PNG);
    }

    @Test
    void storesInsideSubDirectory() {
        StoredFile stored = service().store(part("a.pdf", "application/pdf", PDF), UploadCategory.DOCUMENT, "42").block();

        assertThat(stored.getStorageKey()).startsWith("42/").endsWith(".pdf");
        assertThat(Files.exists(root.resolve(stored.getStorageKey()))).isTrue();
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredType() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThatThrownBy(() -> service().store(part("x.png", "image/png", html), UploadCategory.IMAGE).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service().store(part("x.svg", "image/svg+xml", PNG), UploadCategory.IMAGE).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsTraversalInSubDirectory() {
        assertThatThrownBy(() -> service().store(part("a.png", "image/png", PNG), UploadCategory.IMAGE, "..").block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesOwnFileAndIgnoresForeignKeys() throws Exception {
        LocalFileStorageService service = service();
        StoredFile stored = service.store(part("a.png", "image/png", PNG), UploadCategory.IMAGE).block();

        Path outside = Files.writeString(root.getParent().resolve("keep-" + System.nanoTime() + ".txt"), "x");
        try {
            // legacy Cloudinary public id and a traversal attempt: both must be no-ops
            service.delete("zivdahonlinegrocery/products/abc123").block();
            service.delete("../" + outside.getFileName()).block();
            service.delete(null).block();
            assertThat(Files.exists(outside)).isTrue();

            service.delete(stored.getStorageKey()).block();
            assertThat(Files.exists(root.resolve(stored.getStorageKey()))).isFalse();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    private static FilePart part(String filename, String contentType, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return new FilePart() {
            @Override public String filename() { return filename; }
            @Override public Mono<Void> transferTo(Path dest) { return Mono.error(new UnsupportedOperationException()); }
            @Override public String name() { return "image"; }
            @Override public HttpHeaders headers() { return headers; }
            @Override public Flux<DataBuffer> content() {
                return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));
            }
        };
    }
}
