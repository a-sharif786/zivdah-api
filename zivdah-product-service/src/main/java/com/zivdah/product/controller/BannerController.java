package com.zivdah.product.controller;

import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/banner/getAll")
    public Mono<ResponseEntity<ApiResponse<List<BannerResponseDto>>>> getBanners() {
        return bannerService.getBanners()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<BannerResponseDto>>builder()
                        .status("success").statusCode(200).message("Banners fetched successfully").data(list).build()));
    }
}
