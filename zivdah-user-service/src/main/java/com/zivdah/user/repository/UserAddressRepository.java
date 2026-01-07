package com.zivdah.user.repository;

import com.zivdah.user.entity.UserAddress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    long countByUserId(Long userId);

    // 🔹 Get addresses for a user (optionally pageable)
    List<UserAddress> findByUserId(Long userId, Pageable pageable);

    // 🔹 Reset default address flag for a user
    @Modifying
    @Query("UPDATE UserAddress u SET u.isDefault = false WHERE u.userId = :userId")
    void resetDefaultAddress(Long userId);

}
