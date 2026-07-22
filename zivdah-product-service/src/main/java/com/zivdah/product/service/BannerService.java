package com.zivdah.product.service;

import com.zivdah.product.dto.BannerResponseDto;
import reactor.core.publisher.Flux;

public interface BannerService {
    Flux<BannerResponseDto> getBanners();
}
