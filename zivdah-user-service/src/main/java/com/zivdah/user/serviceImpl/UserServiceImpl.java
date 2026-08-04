package com.zivdah.user.serviceImpl;

import com.zivdah.user.dto.*;
import com.zivdah.user.entity.UserAddress;
import com.zivdah.user.repository.UserAddressRepository;
import com.zivdah.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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
    public Mono<UserResponseDTO> getProfileByUserId(Long userId, String token) {
        return webClient.get()
                .uri(AUTH_SERVICE_URL + "/byUserId/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .flatMap(resp -> resp.getData() != null
                        ? Mono.just(resp.getData())
                        : Mono.error(new RuntimeException("User not found in Auth Service")));
    }



    @Override
    public Flux<AddressResponseDTO> getAddressesByUserId(
            Long userId,
            Pageable pageable,
            String token) {
        log.info(
                "Fetching addresses for userId {}",
                userId
        );


        Mono<UserResponseDTO> userMono = webClient.get()
                .uri(AUTH_SERVICE_URL + "/byUserId/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .map(ApiResponse::getData);


        return   userMono.flatMapMany(user ->
                userAddressRepository.findByUserId(userId, pageable)
                        .map(address -> mapToResponse(address, user))
        );
    }



    @Override
    public Mono<UserResponseDTO> updateProfile(String mobile, UpdateUserProfileDTO dto, String token) {
        return webClient.put()
                .uri(AUTH_SERVICE_URL + "/update-profile/{mobile}", mobile)
                .header(HttpHeaders.AUTHORIZATION, token)

                .bodyValue(dto)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .flatMap(resp -> resp.getData() != null
                        ? Mono.just(resp.getData())
                        : Mono.error(new RuntimeException("Failed to update profile")));
    }

    @Override
    public Mono<AddressResponseDTO> addAddress(Long userId, AddressRequestDTO dto,String token) {

        Mono<UserResponseDTO> userMono = webClient.get()
                .uri(AUTH_SERVICE_URL + "/byUserId/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, token)

                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .map(ApiResponse::getData);

        return userAddressRepository.countByUserId(userId)
                .flatMap(count -> {
                    boolean isFirst = count == 0;

                    if (Boolean.TRUE.equals(dto.getIsDefault()) || isFirst) {
                        return userAddressRepository.resetDefaultAddress(userId)
                                .thenReturn(isFirst);
                    }

                    return Mono.just(isFirst);
                })
                .flatMap(isFirst -> {
                    UserAddress address = UserAddress.builder()
                            .userId(userId)
                            .addressLine1(dto.getAddressLine1())
                            .addressLine2(dto.getAddressLine2())
                            .city(dto.getCity())
                            .state(dto.getState())
                            .pinCode(dto.getPinCode())
                            .isDefault(Boolean.TRUE.equals(dto.getIsDefault()) || isFirst)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return userAddressRepository.save(address);
                })
                .zipWith(userMono)
                .map(tuple -> mapToResponse(tuple.getT1(), tuple.getT2()));
    }

//    @Override
//    public Mono<AddressResponseDTO> addAddress(Long userId, AddressRequestDTO dto) {
//        return userAddressRepository.countByUserId(userId)
//                .flatMap(count -> {
//                    boolean isFirst = count == 0;
//                    if (Boolean.TRUE.equals(dto.getIsDefault()) || isFirst) {
//                        return userAddressRepository.resetDefaultAddress(userId).then(Mono.just(true));
//                    }
//                    return Mono.just(false);
//                })
//                .flatMap(__ -> {
//                    UserAddress address = UserAddress.builder()
//                            .userId(userId)
//                            .addressLine1(dto.getAddressLine1())
//                            .addressLine2(dto.getAddressLine2())
//                            .city(dto.getCity())
//                            .state(dto.getState())
//                            .pinCode(dto.getPinCode())
//                            .isDefault(Boolean.TRUE.equals(dto.getIsDefault()))
//                            .createdAt(LocalDateTime.now())
//                            .build();
//                    return userAddressRepository.save(address);
//                })
//                .map(this::mapToResponse);
//    }

    @Override
    public Flux<AddressResponseDTO> getAddresses(Long userId, Pageable pageable,String token) {

        Mono<UserResponseDTO> userMono = webClient.get()
                .uri(AUTH_SERVICE_URL + "/byUserId/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, token)

                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserResponseDTO>>() {})
                .map(ApiResponse::getData);

        return userMono.flatMapMany(user ->
                userAddressRepository.findByUserId(userId, pageable)
                        .map(address -> mapToResponse(address, user))
        );
       // return userAddressRepository.findByUserId(userId, pageable).map(this::mapToResponse);
    }



    private AddressResponseDTO mapToResponse(UserAddress a, UserResponseDTO user) {
        return AddressResponseDTO.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .role(user.getRole())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .city(a.getCity())
                .state(a.getState())
                .pinCode(a.getPinCode())
                .isDefault(a.getIsDefault())
                .build();
    }


}
