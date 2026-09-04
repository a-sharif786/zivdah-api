package com.zivdah.product.serviceImpl;

import com.zivdah.product.dto.CategoryRequestDto;
import com.zivdah.product.dto.CategoryResponseDto;
import com.zivdah.product.entity.Category;
import com.zivdah.product.repository.CategoryRepository;
import com.zivdah.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public Flux<CategoryResponseDto> getCategories() {
        return categoryRepository.findByActiveTrue().map(this::mapToResponse);
    }

    @Override
    public Flux<CategoryResponseDto> getAllCategoriesForAdmin() {
        return categoryRepository.findAll().map(this::mapToResponse);
    }

    @Override
    public Mono<CategoryResponseDto> getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id)))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<CategoryResponseDto> createCategory(CategoryRequestDto dto) {
        String base = slugify(dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : dto.getName());
        return uniqueSlug(base, null)
                .flatMap(slug -> {
                    Category category = Category.builder()
                            .name(dto.getName())
                            .slug(slug)
                            .parentId(dto.getParentId())
                            .imageUrl(dto.getImageUrl())
                            .active(dto.getActive() != null ? dto.getActive() : true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return categoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Category created: {}", c.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<CategoryResponseDto> updateCategory(Long id, CategoryRequestDto dto) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id)))
                .flatMap(category -> {
                    category.setName(dto.getName());
                    category.setParentId(dto.getParentId());
                    category.setImageUrl(dto.getImageUrl());
                    if (dto.getActive() != null) category.setActive(dto.getActive());
                    category.setUpdatedAt(LocalDateTime.now());

                    // Slug is stable across updates unless the caller explicitly changes it —
                    // regenerating it from name on every rename would break existing links.
                    if (dto.getSlug() != null && !dto.getSlug().isBlank()) {
                        String base = slugify(dto.getSlug());
                        return uniqueSlug(base, id).flatMap(slug -> {
                            category.setSlug(slug);
                            return categoryRepository.save(category);
                        });
                    }
                    return categoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Category updated: {}", c.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Void> deleteCategory(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id)))
                .flatMap(category -> categoryRepository.deleteById(id))
                .doOnSuccess(v -> log.info("Category deleted: {}", id));
    }

    @Override
    public Mono<CategoryResponseDto> toggleActive(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id)))
                .flatMap(category -> {
                    category.setActive(!Boolean.TRUE.equals(category.getActive()));
                    category.setUpdatedAt(LocalDateTime.now());
                    return categoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Category {} active -> {}", c.getId(), c.getActive()))
                .map(this::mapToResponse);
    }

    private String slugify(String input) {
        String s = input.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "category" : s;
    }

    // Appends -2, -3, ... to `base` until a free slug is found (excluding `excludeId`'s
    // own row when updating, so re-saving a category without changing its slug doesn't
    // collide with itself).
    private Mono<String> uniqueSlug(String base, Long excludeId) {
        return tryCandidate(base, base, excludeId, 1);
    }

    private Mono<String> tryCandidate(String base, String candidate, Long excludeId, int attempt) {
        Mono<Boolean> exists = excludeId == null
                ? categoryRepository.existsBySlug(candidate)
                : categoryRepository.existsBySlugAndIdNot(candidate, excludeId);
        return exists.flatMap(taken -> {
            if (!taken) return Mono.just(candidate);
            String next = base + "-" + (attempt + 1);
            return tryCandidate(base, next, excludeId, attempt + 1);
        });
    }

    private CategoryResponseDto mapToResponse(Category category) {
        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parentId(category.getParentId())
                .imageUrl(category.getImageUrl())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
