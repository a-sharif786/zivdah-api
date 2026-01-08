package com.zivdah.review.controller;

import com.zivdah.review.dto.ReviewRequestDto;
import com.zivdah.review.dto.ReviewResponseDto;
import com.zivdah.review.dto.ApiResponse;
import com.zivdah.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewService reviewService;

    // Create review
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> createReview(
            @Valid @RequestBody ReviewRequestDto dto) {

        ReviewResponseDto response = reviewService.createReview(dto);

        return ResponseEntity.ok(ApiResponse.<ReviewResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Review created successfully")
                .data(response)
                .build());
    }

    // Get review by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> getReview(@PathVariable Long id) {

        ReviewResponseDto response = reviewService.getReviewById(id);

        return ResponseEntity.ok(ApiResponse.<ReviewResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Review retrieved successfully")
                .data(response)
                .build());
    }

    // Get all reviews (with optional pagination)
    @GetMapping("")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<ReviewResponseDto> reviews = reviewService.getAllReviews(page, size);

        return ResponseEntity.ok(ApiResponse.<List<ReviewResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Reviews retrieved successfully")
                .data(reviews)
                .build());
    }

    // Update review
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequestDto dto) {

        ReviewResponseDto response = reviewService.updateReview(id, dto);

        return ResponseEntity.ok(ApiResponse.<ReviewResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Review updated successfully")
                .data(response)
                .build());
    }

    // Delete review
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long id) {

        reviewService.deleteReview(id);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Review deleted successfully")
                .data(null)
                .build());
    }
}
