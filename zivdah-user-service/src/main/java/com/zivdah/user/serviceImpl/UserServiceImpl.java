package com.zivdah.user.serviceImpl;

import com.zivdah.user.dto.*;
import com.zivdah.user.entity.UserAddress;
import com.zivdah.user.repository.UserAddressRepository;
import com.zivdah.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAddressRepository userAddressRepository;
    private final WebClient webClient;
    private static final String AUTH_SERVICE_URL = "http://localhost:8001/restful/v1/api/auth";

    @Override
    public Mono<UserResponseDTO> getProfileByMobile(String mobile) {
        return webClient.get()
                .uri(AUTH_SERVICE_URL + "/byMobile/{mobile}", mobile)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .flatMap(resp -> resp.getData() != null
                        ? Mono.just(resp.getData())
                        : Mono.error(new RuntimeException("User not found in Auth Service")));
    }

    @Override
    public Mono<UserResponseDTO> updateProfile(String mobile, UpdateUserProfileDTO dto) {
        return webClient.put()
                .uri(AUTH_SERVICE_URL + "/update-profile/{mobile}", mobile)
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .flatMap(resp -> resp.getData() != null
                        ? Mono.just(resp.getData())
                        : Mono.error(new RuntimeException("Failed to update profile")));
    }

    @Override
    public Mono<Void> resetPassword(Long userId, ResetPasswordDTO dto) {
        log.info("Password reset requested for user {}", userId);
        return Mono.empty();
    }

    @Override
    public Mono<AddressResponseDTO> addAddress(Long userId, AddressRequestDTO dto) {
        return userAddressRepository.countByUserId(userId)
                .flatMap(count -> {
                    boolean isFirst = count == 0;
                    if (Boolean.TRUE.equals(dto.getIsDefault()) || isFirst) {
                        return userAddressRepository.resetDefaultAddress(userId).then(Mono.just(true));
                    }
                    return Mono.just(false);
                })
                .flatMap(__ -> {
                    UserAddress address = UserAddress.builder()
                            .userId(userId)
                            .addressLine1(dto.getAddressLine1())
                            .addressLine2(dto.getAddressLine2())
                            .city(dto.getCity())
                            .state(dto.getState())
                            .pinCode(dto.getPinCode())
                            .isDefault(Boolean.TRUE.equals(dto.getIsDefault()))
                            .createdAt(LocalDateTime.now())
                            .build();
                    return userAddressRepository.save(address);
                })
                .map(this::mapToResponse);
    }

    @Override
    public Flux<AddressResponseDTO> getAddresses(Long userId, Pageable pageable) {
        return userAddressRepository.findByUserId(userId, pageable).map(this::mapToResponse);
    }

    @Override
    public Flux<AuthUserDTO> getAllUsersFromAuth() {
        return webClient.get()
                .uri(AUTH_SERVICE_URL + "/all-users")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<java.util.List<AuthUserDTO>>>() {})
                .flatMapMany(resp -> resp.getData() != null
                        ? Flux.fromIterable(resp.getData())
                        : Flux.error(new RuntimeException("No users found")));
    }

    private AddressResponseDTO mapToResponse(UserAddress a) {
        return AddressResponseDTO.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .city(a.getCity())
                .state(a.getState())
                .pinCode(a.getPinCode())
                .isDefault(a.getIsDefault())
                .build();
    }
}
