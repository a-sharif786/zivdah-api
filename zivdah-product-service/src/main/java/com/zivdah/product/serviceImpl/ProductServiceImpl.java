package com.zivdah.product.serviceImpl;

import com.zivdah.common.event.ProductCreatedEvent;
import com.zivdah.common.upload.CloudinaryUploadResult;
import com.zivdah.common.upload.CloudinaryUploadService;
import com.zivdah.common.upload.UploadCategory;
import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.entity.WishlistEntity;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.kafka.ProductKafkaProducer;
import com.zivdah.product.repository.ProductRepository;
import com.zivdah.product.repository.WishlistRepository;
import com.zivdah.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final ProductKafkaProducer productKafkaProducer;

    @Value("${cloudinary.folder}")
    private String cloudinaryFolder;

    private String productsFolder() {
        return cloudinaryFolder + "/products";
    }

    @Override
    public Mono<ProductResponseDto> createProduct(ProductRequestDto dto, FilePart image, Long currentUserId, String role) {
        log.info("Controller reached");
        log.info("image>>>>>>"+image);
        if (image == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product image is required"));
        }
        Long vendorId = "VENDOR".equalsIgnoreCase(role) ? currentUserId : null;
        return cloudinaryUploadService.upload(image, UploadCategory.IMAGE, productsFolder())
                .flatMap(uploaded -> {
                    ProductEntity entity = ProductEntity.builder()
                            .name(dto.getName()).category(dto.getCategory()).price(dto.getPrice())
                            .discountPrice(dto.getDiscountPrice()).unit(dto.getUnit())
                            .stockQuantity(dto.getStockQuantity()).expiryDate(dto.getExpiryDate())
                            .description(dto.getDescription())
                            .fav(dto.getFav())
                            .organic(dto.getOrganic()).brand(dto.getBrand())
                            .inStock(dto.getStockQuantity() != null && dto.getStockQuantity() > 0)
                            .vendorId(vendorId)
                            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                            .build();
                    applyUploadResult(entity, uploaded);
                    return productRepository.save(entity);
                })
                .doOnSuccess(p -> {
                    log.info("Product created: {}", p.getId());
                    // Seed an inventory row for this product (async, via Kafka) so checkout
                    // never fails with "Inventory not found" for a brand-new product — see
                    // InventoryEventConsumer#onProductCreated in zivdah-inventory-service.
                    productKafkaProducer.publishProductCreated(ProductCreatedEvent.builder()
                            .productId(p.getId())
                            .initialStockQuantity(dto.getStockQuantity() != null ? dto.getStockQuantity() : 0)
                            .build());
                })
                .map(this::mapToResponse);
    }

    @Override
    public Mono<ProductResponseDto> getProductById(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .map(this::mapToResponse);
    }

    @Override
    public Flux<ProductResponseDto> getAllProducts(Pageable pageable) {
        return productRepository.findAllBy(pageable).map(this::mapToResponse);
    }

    @Override
    public Flux<ProductResponseDto> getProductsByCategory(ProductCategory category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable).map(this::mapToResponse);
    }

    @Override
    public Flux<ProductResponseDto> searchProducts(String keyword, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCase(keyword, pageable).map(this::mapToResponse);
    }

    @Override
    public Flux<ProductResponseDto> getWishlist(Long userId, Pageable pageable) {
        return wishlistRepository.findByUserId(userId, pageable)
                .flatMap(wishlist -> productRepository.findById(wishlist.getProductId()))
                .map(entity -> mapToResponse(entity).toBuilder().fav(true).build());
    }

    @Override
    public Mono<ProductResponseDto> updateProduct(Long id, ProductRequestDto dto, FilePart image, Long currentUserId, String role) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .flatMap(entity -> {
                    if ("VENDOR".equalsIgnoreCase(role) && !currentUserId.equals(entity.getVendorId())) {
                        return Mono.<ProductEntity>error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this product"));
                    }
                    entity.setName(dto.getName()); entity.setCategory(dto.getCategory());
                    entity.setPrice(dto.getPrice()); entity.setDiscountPrice(dto.getDiscountPrice());
                    entity.setUnit(dto.getUnit()); entity.setStockQuantity(dto.getStockQuantity());
                    entity.setExpiryDate(dto.getExpiryDate()); entity.setDescription(dto.getDescription());
                    entity.setOrganic(dto.getOrganic());
                    entity.setFav(dto.getFav());
                    entity.setBrand(dto.getBrand());
                    entity.setInStock(dto.getStockQuantity() != null && dto.getStockQuantity() > 0);
                    entity.setUpdatedAt(LocalDateTime.now());

                    if (image == null) {
                        return productRepository.save(entity);
                    }
                    String oldPublicId = entity.getImagePublicId();
                    String oldResourceType = entity.getImageResourceType();
                    return cloudinaryUploadService.upload(image, UploadCategory.IMAGE, productsFolder())
                            .flatMap(uploaded -> {
                                applyUploadResult(entity, uploaded);
                                return productRepository.save(entity);
                            })
                            .flatMap(saved -> cloudinaryUploadService.delete(oldPublicId, oldResourceType).thenReturn(saved));
                })
                .doOnSuccess(p -> log.info("Product updated: {}", p.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Void> deleteProduct(Long id, Long currentUserId, String role) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .flatMap(entity -> {
                    if ("VENDOR".equalsIgnoreCase(role) && !currentUserId.equals(entity.getVendorId())) {
                        return Mono.<ProductEntity>error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this product"));
                    }
                    return cloudinaryUploadService.delete(entity.getImagePublicId(), entity.getImageResourceType())
                            .then(productRepository.deleteById(id))
                            .thenReturn(entity);
                })
                .doOnSuccess(v -> log.info("Product deleted: {}", id))
                .then();
    }

    @Override
    public Mono<List<ProductCategory>> getAllCategories() {
        return Mono.just(Arrays.asList(ProductCategory.values()));
    }

    @Override
    public Mono<ProductResponseDto> updateWishlist(Long userId, Long productId, Boolean fav) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId)))
                .flatMap(product -> {
                    Mono<Void> mutation = Boolean.TRUE.equals(fav)
                            ? wishlistRepository.findByUserIdAndProductId(userId, productId)
                                    .switchIfEmpty(Mono.defer(() -> wishlistRepository.save(WishlistEntity.builder()
                                            .userId(userId).productId(productId).createdAt(LocalDateTime.now()).build())))
                                    .then()
                            : wishlistRepository.deleteByUserIdAndProductId(userId, productId);
                    return mutation.thenReturn(mapToResponse(product).toBuilder().fav(Boolean.TRUE.equals(fav)).build());
                })
                .doOnSuccess(p -> log.info("Wishlist updated for user {} product {} -> fav={}", userId, productId, fav));
    }

    private void applyUploadResult(ProductEntity entity, CloudinaryUploadResult uploaded) {
        entity.setImageUrl(uploaded.getSecureUrl());
        entity.setImagePublicId(uploaded.getPublicId());
        entity.setImageResourceType(uploaded.getResourceType());
        entity.setImageFormat(uploaded.getFormat());
        entity.setImageSizeBytes(uploaded.getBytes());
    }

    @Override
    public Flux<ProductResponseDto> getProductsByVendor(Long vendorId, Pageable pageable) {
        return productRepository.findByVendorId(vendorId, pageable).map(this::mapToResponse);
    }

    @Override
    public Mono<Long> countProducts() {
        return productRepository.count();
    }

    private ProductResponseDto mapToResponse(ProductEntity e) {
        return ProductResponseDto.builder()
                .id(e.getId()).name(e.getName()).category(e.getCategory())
                .price(e.getPrice()).discountPrice(e.getDiscountPrice()).unit(e.getUnit())
                .stockQuantity(e.getStockQuantity()).inStock(e.getInStock()).expiryDate(e.getExpiryDate())
                .description(e.getDescription()).imageUrl(e.getImageUrl())
                .organic(e.getOrganic()).brand(e.getBrand())
                .fav(e.getFav())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .vendorId(e.getVendorId())
                .build();
    }
}
