package com.zivdah.auth.service;

import com.zivdah.auth.dto.UpdateUserAddressRequestDTO;
import com.zivdah.auth.dto.UserAddressRequestDTO;

public interface UserAddressService {
    void saveAddress(UserAddressRequestDTO request);
    void updateUserAndAddress(UpdateUserAddressRequestDTO request);
}
