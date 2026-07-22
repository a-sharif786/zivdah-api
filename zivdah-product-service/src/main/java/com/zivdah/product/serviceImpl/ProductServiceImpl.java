package com.zivdah.product.serviceImpl;

import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.repository.ProductRepository;
import com.zivdah.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

    @Override
    public Mono<ProductResponseDto> createProduct(ProductRequestDto dto) {
        ProductEntity entity = ProductEntity.builder()
                .name(dto.getName()).category(dto.getCategory()).price(dto.getPrice())
                .discountPrice(dto.getDiscountPrice()).unit(dto.getUnit())
                .stockQuantity(dto.getStockQuantity()).expiryDate(dto.getExpiryDate())
                .description(dto.getDescription()).imageUrl(dto.getImageUrl())
                .organic(dto.getOrganic()).brand(dto.getBrand())
                .fav(dto.getFav() != null ? dto.getFav() : false)
                .inStock(dto.getStockQuantity() != null && dto.getStockQuantity() > 0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        return productRepository.save(entity)
                .doOnSuccess(p -> log.info("Product created: {}", p.getId()))
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
    public Flux<ProductResponseDto> getAllWishlist(Pageable pageable) {
        return productRepository.findByFavTrue(pageable).map(this::mapToResponse);
    }

    @Override
    public Mono<ProductResponseDto> updateProduct(Long id, ProductRequestDto dto) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .flatMap(entity -> {
                    entity.setName(dto.getName()); entity.setCategory(dto.getCategory());
                    entity.setPrice(dto.getPrice()); entity.setDiscountPrice(dto.getDiscountPrice());
                    entity.setUnit(dto.getUnit()); entity.setStockQuantity(dto.getStockQuantity());
                    entity.setExpiryDate(dto.getExpiryDate()); entity.setDescription(dto.getDescription());
                    entity.setImageUrl(dto.getImageUrl()); entity.setOrganic(dto.getOrganic());
                    entity.setBrand(dto.getBrand()); entity.setFav(dto.getFav());
                    entity.setInStock(dto.getStockQuantity() != null && dto.getStockQuantity() > 0);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(entity);
                })
                .doOnSuccess(p -> log.info("Product updated: {}", p.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Void> deleteProduct(Long id) {
        return productRepository.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.<Void>error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
                    return productRepository.deleteById(id);
                })
                .doOnSuccess(v -> log.info("Product deleted: {}", id));
    }

    @Override
    public Mono<List<ProductCategory>> getAllCategories() {
        return Mono.just(Arrays.asList(ProductCategory.values()));
    }

    @Override
    public Mono<ProductResponseDto> updateWishlist(Long id, Boolean fav) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)))
                .flatMap(entity -> {
                    entity.setFav(fav);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(entity);
                })
                .doOnSuccess(p -> log.info("Wishlist updated for product {} -> fav={}", id, fav))
                .map(this::mapToResponse);
    }

    private ProductResponseDto mapToResponse(ProductEntity e) {
        return ProductResponseDto.builder()
                .id(e.getId()).name(e.getName()).category(e.getCategory())
                .price(e.getPrice()).discountPrice(e.getDiscountPrice()).unit(e.getUnit())
                .stockQuantity(e.getStockQuantity()).expiryDate(e.getExpiryDate())
                .description(e.getDescription()).imageUrl(e.getImageUrl())
                .organic(e.getOrganic()).brand(e.getBrand())
                .createdAt(e.getCreatedAt()).fav(e.getFav())
                .build();
    }
}
