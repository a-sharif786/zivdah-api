package com.zivdah.product.service;

import com.zivdah.product.dto.BannerRequestDto;
import com.zivdah.product.dto.BannerResponseDto;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BannerService {
    Flux<BannerResponseDto> getBanners();
    Flux<BannerResponseDto> getAllBannersForAdmin();
    Mono<BannerResponseDto> createBanner(BannerRequestDto dto, FilePart image);
    Mono<BannerResponseDto> updateBanner(Long id, BannerRequestDto dto, FilePart image);
    Mono<Void> deleteBanner(Long id);
    Mono<BannerResponseDto> toggleActive(Long id);
}
