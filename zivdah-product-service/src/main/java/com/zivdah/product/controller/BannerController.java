package com.zivdah.product.controller;

import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.BannerRequestDto;
import com.zivdah.product.dto.BannerResponseDto;
import com.zivdah.product.service.BannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/banner")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/getAll")
    public Mono<ResponseEntity<ApiResponse<List<BannerResponseDto>>>> getBanners() {
        return bannerService.getBanners()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<BannerResponseDto>>builder()
                        .status("success").statusCode(200).message("Banners fetched successfully").data(list).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<BannerResponseDto>>>> getAllBannersForAdmin() {
        return bannerService.getAllBannersForAdmin()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<BannerResponseDto>>builder()
                        .status("success").statusCode(200).message("Banners fetched successfully").data(list).build()));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<BannerResponseDto>>> createBanner(
            @Valid @RequestPart("data") BannerRequestDto dto,
            @RequestPart("image") FilePart image) {
        return bannerService.createBanner(dto, image)
                .map(r -> ResponseEntity.ok(ApiResponse.<BannerResponseDto>builder()
                        .status("success").statusCode(200).message("Banner created successfully").data(r).build()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<BannerResponseDto>>> updateBanner(
            @PathVariable Long id, @Valid @RequestPart("data") BannerRequestDto dto,
            @RequestPart(value = "image", required = false) FilePart image) {
        return bannerService.updateBanner(id, dto, image)
                .map(r -> ResponseEntity.ok(ApiResponse.<BannerResponseDto>builder()
                        .status("success").statusCode(200).message("Banner updated successfully").data(r).build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteBanner(@PathVariable Long id) {
        return bannerService.deleteBanner(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Banner deleted successfully").build()));
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<BannerResponseDto>>> toggleActive(@PathVariable Long id) {
        return bannerService.toggleActive(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<BannerResponseDto>builder()
                        .status("success").statusCode(200).message("Banner status toggled").data(r).build()));
    }
}
