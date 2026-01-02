package com.zivdah.product.repository;

import com.zivdah.product.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByActiveTrue();
}
