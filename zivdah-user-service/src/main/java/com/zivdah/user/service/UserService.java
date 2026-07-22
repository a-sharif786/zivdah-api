package com.zivdah.user.service;

import com.zivdah.user.dto.*;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<UserResponseDTO> getProfileByMobile(String mobile);
    Mono<UserResponseDTO> updateProfile(String mobile, UpdateUserProfileDTO dto);
    Mono<Void> resetPassword(Long userId, ResetPasswordDTO dto);
    Mono<AddressResponseDTO> addAddress(Long userId, AddressRequestDTO dto);
    Flux<AddressResponseDTO> getAddresses(Long userId, Pageable pageable);
    Flux<AuthUserDTO> getAllUsersFromAuth();
}
