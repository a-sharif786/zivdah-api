package com.zivdah.review.serviceImpl;

import com.zivdah.review.dto.ReviewRequestDto;
import com.zivdah.review.dto.ReviewResponseDto;
import com.zivdah.review.entity.Review;
import com.zivdah.review.repository.ReviewRepository;
import com.zivdah.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;

    @Override
    public Mono<ReviewResponseDto> createReview(ReviewRequestDto dto) {
        Review review = Review.builder()
                .userId(dto.getUserId())
                .productId(dto.getProductId())
                .rating(dto.getRating())
                .comment(dto.getComment())
                .createdAt(LocalDateTime.now())
                .build();
        return reviewRepository.save(review).map(this::mapToDto);
    }

    @Override
    public Mono<ReviewResponseDto> getReviewById(Long id) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found: " + id)))
                .map(this::mapToDto);
    }

    @Override
    public Flux<ReviewResponseDto> getAllReviews(int page, int size) {
        return reviewRepository.findAllBy(PageRequest.of(page, size)).map(this::mapToDto);
    }

    @Override
    public Mono<ReviewResponseDto> updateReview(Long id, ReviewRequestDto dto) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found: " + id)))
                .flatMap(review -> {
                    review.setRating(dto.getRating());
                    review.setComment(dto.getComment());
                    return reviewRepository.save(review);
                })
                .map(this::mapToDto);
    }

    @Override
    public Mono<Void> deleteReview(Long id) {
        return reviewRepository.existsById(id)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.<Void>error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found: " + id));
                    }
                    return reviewRepository.deleteById(id);
                });
    }

    @Override
    public Flux<ReviewResponseDto> getReviewsByProduct(Long productId, int page, int size) {
        return reviewRepository.findByProductId(productId, PageRequest.of(page, size)).map(this::mapToDto);
    }

    private ReviewResponseDto mapToDto(Review review) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .productId(review.getProductId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getCreatedAt())
                .build();
    }
}
