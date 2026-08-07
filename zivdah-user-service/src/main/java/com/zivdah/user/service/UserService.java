package com.zivdah.user.service;

import com.zivdah.user.dto.*;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<UserResponseDTO> getProfileByUserId(Long userId , String token);
    Mono<UserResponseDTO> updateProfile(String mobile, UpdateUserProfileDTO dto, String token);
    Mono<AddressResponseDTO> addAddress(Long userId, AddressRequestDTO dto, String token);
    Flux<AddressResponseDTO> getAddresses(Long userId, Pageable pageable, String token);
    Flux<AddressResponseDTO> getAddressesByUserId(Long userId, Pageable pageable, String token);
}
