package com.zivdah.review.controller;

import com.zivdah.review.dto.ApiResponse;
import com.zivdah.review.dto.ReviewRequestDto;
import com.zivdah.review.dto.ReviewResponseDto;
import com.zivdah.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/create")
    public Mono<ResponseEntity<ApiResponse<ReviewResponseDto>>> createReview(
            @Valid @RequestBody ReviewRequestDto dto) {
        return reviewService.createReview(dto)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.<ReviewResponseDto>builder()
                                .status("success").statusCode(201)
                                .message("Review created successfully").data(r).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ReviewResponseDto>>> getReview(@PathVariable Long id) {
        return reviewService.getReviewById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<ReviewResponseDto>builder()
                        .status("success").statusCode(200)
                        .message("Review retrieved successfully").data(r).build()));
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<ReviewResponseDto>>>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.getAllReviews(page, size)
                .collectList()
                .map(reviews -> ResponseEntity.ok(ApiResponse.<List<ReviewResponseDto>>builder()
                        .status("success").statusCode(200)
                        .message("Reviews retrieved successfully").data(reviews).build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ReviewResponseDto>>> updateReview(
            @PathVariable Long id, @Valid @RequestBody ReviewRequestDto dto) {
        return reviewService.updateReview(id, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<ReviewResponseDto>builder()
                        .status("success").statusCode(200)
                        .message("Review updated successfully").data(r).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteReview(@PathVariable Long id) {
        return reviewService.deleteReview(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200)
                        .message("Review deleted successfully").build()));
    }
}
