package com.zivdah.user.controller;

import com.zivdah.user.dto.*;
import com.zivdah.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {

    private final UserService userService;

    // 🔹 Get Profile
//    @GetMapping("/getProfile")
//    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getProfile(Authentication authentication) {
//
//
//        log.info("Authentication Object: {}", authentication.getName());
//
//        String mobile = authentication.getName();
//
//        UserProfileResponseDTO profile = userService.getProfileByMobile(mobile);
//
//        log.info("Authentication Object: {}", profile);
//        return ResponseEntity.ok(
//                ApiResponse.<UserProfileResponseDTO>builder()
//                        .status("success")
//                        .statusCode(HttpStatus.OK.value())
//                        .message("Profile fetched successfully")
//                        .data(profile)
//                        .build()
//        );
//    }


    @GetMapping("/getProfile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getProfile(
            Authentication authentication) {

        String mobile = authentication.getName();

        UserResponseDTO user =
                userService.getProfileByMobile(mobile);

        return ResponseEntity.ok(
                ApiResponse.<UserResponseDTO>builder()
                        .status("success")
                        .statusCode(200)
                        .message("User fetched successfully")
                        .data(user)
                        .build()
        );
    }



    @PutMapping("/updateProfile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileDTO dto) {

        String mobile = authentication.getName(); // JWT subject

        UserResponseDTO updated =
                userService.updateProfile(mobile, dto);

        return ResponseEntity.ok(
                ApiResponse.<UserResponseDTO>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Profile updated successfully")
                        .data(updated)
                        .build()
        );
    }
    // 🔹 Add Address
    @PostMapping("/address")
    public ResponseEntity<ApiResponse<AddressResponseDTO>> addAddress(
            Authentication authentication,
          @Valid  @RequestBody AddressRequestDTO dto) {

        Long userId = Long.parseLong(authentication.getName());

        AddressResponseDTO response = userService.addAddress(userId, dto);


        return ResponseEntity.ok(
                ApiResponse.<AddressResponseDTO>builder()
                        .status("success")
                        .statusCode(201)
                        .message("Address added successfully")
                        .data(response)
                        .build()
        );
    }

    // 🔹 Get Addresses (with optional paging)
    @GetMapping("/address")
    public ResponseEntity<ApiResponse<List<AddressResponseDTO>>> getAddresses(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = Long.parseLong(authentication.getName());
        Pageable pageable = PageRequest.of(page, size);

        List<AddressResponseDTO> addresses = userService.getAddresses(userId, pageable); // you can paginate in service if needed

        return ResponseEntity.ok(
                ApiResponse.<List<AddressResponseDTO>>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Addresses retrieved successfully")
                        .data(addresses)
                        .build()
        );
    }


    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            Authentication authentication,
            @Valid @RequestBody ResetPasswordDTO dto) {

        Long userId = Long.parseLong(authentication.getName());

        userService.resetPassword(userId, dto);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .status("success")
                        .statusCode(200)
                        .message("Password reset successfully")
                        .data(null)
                        .build()
        );
    }


    @GetMapping("/all-users")
    public ResponseEntity<ApiResponse<List<AuthUserDTO>>> getAllUsers() {

        List<AuthUserDTO> users = userService.getAllUsersFromAuth();

        return ResponseEntity.ok(
                ApiResponse.<List<AuthUserDTO>>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Users fetched from auth service")
                        .data(users)
                        .build()
        );
    }
}
