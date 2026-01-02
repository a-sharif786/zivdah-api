package com.zivdah.cart.repository;

import com.zivdah.cart.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface CartRepository extends JpaRepository<CartItemEntity, Long> {
    List<CartItemEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
