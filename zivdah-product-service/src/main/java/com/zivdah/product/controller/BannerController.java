package com.zivdah.product.controller;


import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BannerController {

    @Autowired
    private BannerService bannerService;

    @GetMapping("/banner/getAll")
    public ResponseEntity<ApiResponse<List<BannerResponseDto>>> getBanners() {
        return ResponseEntity.ok(
                ApiResponse.<List<BannerResponseDto>>builder()
                        .status("success")
                        .statusCode(200)
                        .message("Banners fetched successfully")
                        .data(bannerService.getBanners())
                        .build()
        );
    }
}
