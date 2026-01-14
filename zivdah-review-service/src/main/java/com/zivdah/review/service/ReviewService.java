package com.zivdah.review.service;

import com.zivdah.review.dto.ReviewRequestDto;
import com.zivdah.review.dto.ReviewResponseDto;


import java.util.List;

public interface ReviewService {

    ReviewResponseDto createReview(ReviewRequestDto reviewRequestDto);

    ReviewResponseDto getReviewById(Long id);

    List<ReviewResponseDto> getAllReviews(int page, int size);

    ReviewResponseDto updateReview(Long id, ReviewRequestDto reviewRequestDto);

    void deleteReview(Long id);
}
