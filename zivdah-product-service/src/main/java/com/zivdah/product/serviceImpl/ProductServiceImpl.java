package com.zivdah.product.serviceImpl;

import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.repository.ProductRepository;
import com.zivdah.product.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;
    @Override
    @CacheEvict(value = {"productById", "allProducts"}, allEntries = true)
    public ProductResponseDto createProduct(ProductRequestDto dto) {
        ProductEntity entity = ProductEntity.builder()
                .name(dto.getName())
                .category(dto.getCategory())
                .price(dto.getPrice())
                .discountPrice(dto.getDiscountPrice())
                .unit(dto.getUnit())
                .stockQuantity(dto.getStockQuantity())
                .expiryDate(dto.getExpiryDate())
                .description(dto.getDescription())
                .imageUrl(dto.getImageUrl())
                .organic(dto.getOrganic())
                .brand(dto.getBrand())
                .fav(dto.getFav())
                .build();

        ProductEntity saved = productRepository.save(entity);

        log.info("Product created with id {}", saved.getId());

        return mapToResponse(saved);
    }

    // ================= GET BY ID =================
    @Override
    @Cacheable(value = "productById", key = "#id")
    public ProductResponseDto getProductById(Long id) {
        log.info("!!! DATABASE HIT FOR ID: {} !!!", id); // Watch for this in logs
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Product not found with id: " + id));

        return mapToResponse(entity);
    }

    // ================= GET ALL =================
    @Override
    @Cacheable(value = "allProducts", key = "'page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize")
    public List<ProductResponseDto> getAllProducts(Pageable pageable) {
        log.info("!!! DATABASE HIT FOR PRODUCTS PAGE {} SIZE {} !!!", pageable.getPageNumber(), pageable.getPageSize());
        // Watch for this in logs
        return productRepository.findAll(pageable)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ================= GET ALL WISH List =================
    @Override
    @Cacheable(value = "wishlistProducts", key = "'page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize")
    public List<ProductResponseDto> getAllWishlist(Pageable pageable) {

        // Watch for this in logs
        return productRepository.findByFavTrue(pageable)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ================= UPDATE =================
    @Override
    @CacheEvict(value = {"productById", "allProducts", "wishlistProducts"}, allEntries = true)
    public ProductResponseDto updateProduct(Long id, ProductRequestDto dto) {

        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Product not found with id: " + id));

        entity.setName(dto.getName());
        entity.setCategory(dto.getCategory());
        entity.setPrice(dto.getPrice());
        entity.setDiscountPrice(dto.getDiscountPrice());
        entity.setUnit(dto.getUnit());
        entity.setStockQuantity(dto.getStockQuantity());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setDescription(dto.getDescription());
        entity.setImageUrl(dto.getImageUrl());
        entity.setOrganic(dto.getOrganic());
        entity.setBrand(dto.getBrand());
        entity.setFav(dto.getFav());

        ProductEntity updated = productRepository.save(entity);

        log.info("Product updated with id {}", updated.getId());

        return mapToResponse(updated);
    }

    // ================= DELETE =================
    @Override
    @CacheEvict(value = {"productById", "allProducts", "wishlistProducts"}, allEntries = true)
    public void deleteProduct(Long id) {

        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found with id: " + id);
        }

        productRepository.deleteById(id);
        log.info("Product deleted with id {}", id);
    }

    @Override
    @Cacheable(value = "productCategories", key = "'all'")
    public List<ProductCategory> getAllCategories() {
        return Arrays.asList(ProductCategory.values());
    }



    @Override
    @CacheEvict(
            value = {"productById", "allProducts", "wishlistProducts"},
            allEntries = true
    )
    public ProductResponseDto updateWishlist(Long id, Boolean fav) {

        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Product not found with id: " + id));

        entity.setFav(fav);

        ProductEntity updated = productRepository.save(entity);

        log.info("Wishlist updated for product {} -> fav={}", id, fav);

        return mapToResponse(updated);
    }

    private ProductResponseDto mapToResponse(ProductEntity entity) {

        return ProductResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .category(entity.getCategory())
                .price(entity.getPrice())
                .discountPrice(entity.getDiscountPrice())
                .unit(entity.getUnit())
                .stockQuantity(entity.getStockQuantity())
                .expiryDate(entity.getExpiryDate())
                .description(entity.getDescription())
                .imageUrl(entity.getImageUrl())
                .organic(entity.getOrganic())
                .brand(entity.getBrand())
                .createdAt(entity.getCreatedAt())
                .fav(entity.getFav())
                .build();
    }
}



