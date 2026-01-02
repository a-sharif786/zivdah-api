package com.zivdah.product.serviceImpl;

import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.entity.Banner;
import com.zivdah.product.repository.BannerRepository;
import com.zivdah.product.service.BannerService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {
    @Autowired
    private BannerRepository bannerRepository;

    @Override
    public List<BannerResponseDto> getBanners() {
        return bannerRepository.findByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private BannerResponseDto  mapToResponse(Banner banner) {
        return BannerResponseDto.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .title(banner.getTitle())
                .build();
    }
}
