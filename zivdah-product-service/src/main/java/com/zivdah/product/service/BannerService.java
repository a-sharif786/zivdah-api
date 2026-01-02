package com.zivdah.product.service;

import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.dto.ProductResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface BannerService {
    List<BannerResponseDto> getBanners();
}
