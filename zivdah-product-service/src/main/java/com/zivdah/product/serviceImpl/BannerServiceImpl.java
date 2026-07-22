package com.zivdah.product.serviceImpl;

import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.repository.BannerRepository;
import com.zivdah.product.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    public Flux<BannerResponseDto> getBanners() {
        return bannerRepository.findByActiveTrue()
                .map(banner -> BannerResponseDto.builder()
                        .id(banner.getId())
                        .imageUrl(banner.getImageUrl())
                        .title(banner.getTitle())
                        .build());
    }
}
