package com.zivdah.product.serviceImpl;

import com.zivdah.common.upload.CloudinaryUploadResult;
import com.zivdah.common.upload.CloudinaryUploadService;
import com.zivdah.common.upload.UploadCategory;
import com.zivdah.product.dto.BannerRequestDto;
import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.entity.Banner;
import com.zivdah.product.repository.BannerRepository;
import com.zivdah.product.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final CloudinaryUploadService cloudinaryUploadService;

    @Value("${cloudinary.folder}")
    private String cloudinaryFolder;

    private String bannersFolder() {
        return cloudinaryFolder + "/banners";
    }

    @Override
    public Flux<BannerResponseDto> getBanners() {
        return bannerRepository.findByActiveTrue().map(this::mapToResponse);
    }

    @Override
    public Flux<BannerResponseDto> getAllBannersForAdmin() {
        return bannerRepository.findAll().map(this::mapToResponse);
    }

    @Override
    public Mono<BannerResponseDto> createBanner(BannerRequestDto dto, FilePart image) {
        if (image == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banner image is required"));
        }
        return cloudinaryUploadService.upload(image, UploadCategory.IMAGE, bannersFolder())
                .flatMap(uploaded -> {
                    Banner banner = Banner.builder()
                            .title(dto.getTitle())
                            .active(dto.getActive() != null ? dto.getActive() : true)
                            .build();
                    applyUploadResult(banner, uploaded);
                    return bannerRepository.save(banner);
                })
                .doOnSuccess(b -> log.info("Banner created: {}", b.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<BannerResponseDto> updateBanner(Long id, BannerRequestDto dto, FilePart image) {
        return bannerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found: " + id)))
                .flatMap(banner -> {
                    banner.setTitle(dto.getTitle());
                    if (dto.getActive() != null) banner.setActive(dto.getActive());

                    if (image == null) {
                        return bannerRepository.save(banner);
                    }
                    String oldPublicId = banner.getImagePublicId();
                    String oldResourceType = banner.getImageResourceType();
                    return cloudinaryUploadService.upload(image, UploadCategory.IMAGE, bannersFolder())
                            .flatMap(uploaded -> {
                                applyUploadResult(banner, uploaded);
                                return bannerRepository.save(banner);
                            })
                            .flatMap(saved -> cloudinaryUploadService.delete(oldPublicId, oldResourceType).thenReturn(saved));
                })
                .doOnSuccess(b -> log.info("Banner updated: {}", b.getId()))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Void> deleteBanner(Long id) {
        return bannerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found: " + id)))
                .flatMap(banner -> cloudinaryUploadService.delete(banner.getImagePublicId(), banner.getImageResourceType())
                        .then(bannerRepository.deleteById(id)))
                .doOnSuccess(v -> log.info("Banner deleted: {}", id));
    }

    @Override
    public Mono<BannerResponseDto> toggleActive(Long id) {
        return bannerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found: " + id)))
                .flatMap(banner -> {
                    banner.setActive(!Boolean.TRUE.equals(banner.getActive()));
                    return bannerRepository.save(banner);
                })
                .doOnSuccess(b -> log.info("Banner {} active -> {}", b.getId(), b.getActive()))
                .map(this::mapToResponse);
    }

    private void applyUploadResult(Banner banner, CloudinaryUploadResult uploaded) {
        banner.setImageUrl(uploaded.getSecureUrl());
        banner.setImagePublicId(uploaded.getPublicId());
        banner.setImageResourceType(uploaded.getResourceType());
        banner.setImageFormat(uploaded.getFormat());
        banner.setImageSizeBytes(uploaded.getBytes());
    }

    private BannerResponseDto mapToResponse(Banner banner) {
        return BannerResponseDto.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .title(banner.getTitle())
                .active(banner.getActive())
                .build();
    }
}
