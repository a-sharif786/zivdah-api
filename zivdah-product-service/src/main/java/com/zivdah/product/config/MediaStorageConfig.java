package com.zivdah.product.config;

import com.zivdah.common.upload.LocalFileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Paths;

// Product and banner images are stored on local disk (prod: PRODUCT_STORAGE_PATH /
// BANNER_STORAGE_PATH under /var/www/zivdah-media/, bind-mounted in docker-compose.yml) and served
// by nginx under /media/products/ and /media/banners/ — same model as order-service's invoices.
@Configuration
public class MediaStorageConfig implements WebFluxConfigurer {

    @Value("${media.products.storage-path}")
    private String productsPath;

    @Value("${media.products.public-base-url}")
    private String productsBaseUrl;

    @Value("${media.banners.storage-path}")
    private String bannersPath;

    @Value("${media.banners.public-base-url}")
    private String bannersBaseUrl;

    // Dev only (no nginx locally): serve the stored files from this service itself at /media/**.
    @Value("${media.serve-locally:false}")
    private boolean serveLocally;

    @Bean
    public LocalFileStorageService productImageStorage() {
        return new LocalFileStorageService(productsPath, productsBaseUrl);
    }

    @Bean
    public LocalFileStorageService bannerImageStorage() {
        return new LocalFileStorageService(bannersPath, bannersBaseUrl);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!serveLocally) {
            return;
        }
        registry.addResourceHandler("/media/products/**")
                .addResourceLocations(Paths.get(productsPath).toAbsolutePath().toUri().toString());
        registry.addResourceHandler("/media/banners/**")
                .addResourceLocations(Paths.get(bannersPath).toAbsolutePath().toUri().toString());
    }
}
