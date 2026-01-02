package com.zivdah.auth.repository;

import com.zivdah.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByUserId(Long userId);

}