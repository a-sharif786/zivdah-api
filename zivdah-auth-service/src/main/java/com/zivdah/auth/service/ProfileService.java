package com.zivdah.auth.service;

import com.zivdah.auth.dto.ProfileResponseDTO;

public interface ProfileService {
    ProfileResponseDTO getProfile(Long userId);
}