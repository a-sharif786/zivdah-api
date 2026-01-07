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

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAddressRepository userAddressRepository;
    private final WebClient webClient;
    private final String AUTH_SERVICE_URL  = "http://localhost:8001/restful/v1/api/auth"; // AuthService base URL




//    @Override
//    public UserProfileResponseDTO getProfile(Long userId) {
//        UserProfile profile = userProfileRepository.findByUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Profile not found"));
//
//        return mapProfileToResponse(profile);
//    }
//
//    @Override
//    public UserProfileResponseDTO updateProfile(Long userId, UpdateProfileDTO dto) {
//
//        UserProfile profile = userProfileRepository.findByUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Profile not found"));
//
//        profile.setName(dto.getName());
//        return mapProfileToResponse(userProfileRepository.save(profile));
//    }

    @Override
    public void resetPassword(Long userId, ResetPasswordDTO dto) {
        // Call auth-service via REST if needed
        System.out.println("Password reset requested for user " + userId);
    }

    @Override
    public AddressResponseDTO addAddress(Long userId, AddressRequestDTO dto) {

        boolean isFirstAddress = userAddressRepository.countByUserId(userId) == 0;
        if (Boolean.TRUE.equals(dto.getIsDefault()) || isFirstAddress) {
            userAddressRepository.resetDefaultAddress(userId);
        }
        UserAddress address = UserAddress.builder()
                .userId(userId)
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .pinCode(dto.getPinCode())
                .isDefault(Boolean.TRUE.equals(dto.getIsDefault()))
                .build();

        UserAddress savedAddress = userAddressRepository.save(address);

        return mapToResponse(savedAddress);
    }

    @Override
    public List<AddressResponseDTO> getAddresses(Long userId, Pageable pageable) {
        return userAddressRepository.findByUserId(userId, pageable)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AddressResponseDTO mapToResponse(UserAddress address) {
        return AddressResponseDTO.builder()
                .id(address.getId())
                .userId(address.getUserId())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .pinCode(address.getPinCode())
                .isDefault(address.getIsDefault())
                .build();
    }


//    private UserProfileResponseDTO mapProfileToResponse(UserProfile profile) {
//        return UserProfileResponseDTO.builder()
//                .userId(profile.getUserId())
//                .name(profile.getName())
//                .email(profile.getEmail())
//                .mobile(profile.getMobile())
//                .build();
//    }


    @Override
    public List<AuthUserDTO> getAllUsersFromAuth() {

        ApiResponse<List<AuthUserDTO>> response =
                webClient.get()
                        .uri(AUTH_SERVICE_URL + "/all-users")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<
                                                        ApiResponse<List<AuthUserDTO>>>() {})
                        .block();

        if (response == null || response.getData() == null) {
            throw new RuntimeException("No users found");
        }

        return response.getData();
    }





    @Override
    public UserResponseDTO getProfileByMobile(String mobile) {

        ApiResponse<UserResponseDTO> response =
                webClient.get()
                        .uri(AUTH_SERVICE_URL + "/byMobile/{mobile}", mobile)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<
                                ApiResponse<UserResponseDTO>>() {})
                        .block();

        if (response == null || response.getData() == null) {
            throw new RuntimeException("User not found in Auth Service");
        }

        return response.getData();
    }


    @Override
    public UserResponseDTO updateProfile(String mobile, UpdateUserProfileDTO dto) {

        ApiResponse<UserResponseDTO> response =
                webClient.put()
                        .uri(AUTH_SERVICE_URL + "/update-profile/{mobile}", mobile)
                        .bodyValue(dto)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<
                                ApiResponse<UserResponseDTO>>() {})
                        .block();

        if (response == null || response.getData() == null) {
            throw new RuntimeException("Failed to update profile");
        }

        return response.getData();
    }
}