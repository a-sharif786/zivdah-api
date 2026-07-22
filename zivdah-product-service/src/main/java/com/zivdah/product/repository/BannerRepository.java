package com.zivdah.product.repository;

import com.zivdah.product.entity.Banner;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface BannerRepository extends ReactiveCrudRepository<Banner, Long> {
    Flux<Banner> findByActiveTrue();
}
