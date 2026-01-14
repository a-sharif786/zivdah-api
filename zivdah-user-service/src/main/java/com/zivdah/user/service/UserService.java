package com.zivdah.user.service;

import com.zivdah.user.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserResponseDTO getProfileByMobile(String mobile);
    UserResponseDTO updateProfile(String mobile, UpdateUserProfileDTO dto);


    void resetPassword(Long userId, ResetPasswordDTO dto);

    AddressResponseDTO addAddress(Long userId, AddressRequestDTO dto);

    List<AddressResponseDTO> getAddresses(Long userId, Pageable pageable);

    List<AuthUserDTO> getAllUsersFromAuth();
}
