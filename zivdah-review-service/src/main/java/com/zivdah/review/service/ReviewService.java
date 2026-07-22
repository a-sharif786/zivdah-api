package com.zivdah.review.service;

import com.zivdah.review.dto.ReviewRequestDto;
import com.zivdah.review.dto.ReviewResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReviewService {
    Mono<ReviewResponseDto> createReview(ReviewRequestDto dto);
    Mono<ReviewResponseDto> getReviewById(Long id);
    Flux<ReviewResponseDto> getAllReviews(int page, int size);
    Mono<ReviewResponseDto> updateReview(Long id, ReviewRequestDto dto);
    Mono<Void> deleteReview(Long id);
}
