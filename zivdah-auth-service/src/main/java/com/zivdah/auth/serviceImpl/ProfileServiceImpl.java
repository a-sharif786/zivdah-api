package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.ProfileResponseDTO;
import com.zivdah.auth.entity.UserAddress;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.repository.UserAddressRepository;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Override
    public ProfileResponseDTO getProfile(Long userId) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserAddress userAddress = userAddressRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User Adress not found"));

        String fullAddress =
                userAddress.getHouseNumber() + ", " +
                        userAddress.getAddressLine1() + ", " +
                        userAddress.getCity() + ", " +
                        userAddress.getState() + " - " +
                        userAddress.getPinCode();

        ProfileResponseDTO dto = new ProfileResponseDTO();
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setMobile(user.getMobile());
        dto.setAddress(fullAddress);

        return dto;
    }
}

