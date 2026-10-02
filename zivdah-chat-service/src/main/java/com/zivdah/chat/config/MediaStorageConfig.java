package com.zivdah.chat.config;

import com.zivdah.common.upload.LocalFileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Paths;

// Chat attachments are stored on local disk (prod: CHAT_STORAGE_PATH under /var/www/zivdah-media/,
// bind-mounted in docker-compose.yml) and served by nginx under /media/chat/ — same model as
// product-service's images and order-service's invoices. Files get random UUID names, so a URL is
// only known to the conversation it was posted in.
@Configuration
public class MediaStorageConfig implements WebFluxConfigurer {

    @Value("${media.chat.storage-path}")
    private String chatPath;

    @Value("${media.chat.public-base-url}")
    private String chatBaseUrl;

    // Dev only (no nginx locally): serve the stored files from this service itself at /media/chat/**.
    @Value("${media.serve-locally:false}")
    private boolean serveLocally;

    @Bean
    public LocalFileStorageService chatAttachmentStorage() {
        return new LocalFileStorageService(chatPath, chatBaseUrl);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (serveLocally) {
            registry.addResourceHandler("/media/chat/**")
                    .addResourceLocations(Paths.get(chatPath).toAbsolutePath().toUri().toString());
        }
    }
}
