package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.UpdateUserAddressRequestDTO;
import com.zivdah.auth.dto.UserAddressRequestDTO;
import com.zivdah.auth.entity.UserAddress;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.repository.UserAddressRepository;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.repository.UserSessionRepository;
import com.zivdah.auth.service.UserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAddressServiceImpl implements UserAddressService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void saveAddress(UserAddressRequestDTO request) {
        try {
            UserEntity user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserAddress address = new UserAddress();
            address.setUser(user);
            address.setAddressLine1(request.getAddressLine1());
            address.setHouseNumber(request.getHouseNumber());
            address.setCity(request.getCity());
            address.setState(request.getState());
            address.setPinCode(request.getPinCode());

            userAddressRepository.save(address);

        } catch (DataIntegrityViolationException ex) {
            throw new RuntimeException("Address already exists for this user");
        }
    }

    @Override
    public void updateUserAndAddress(UpdateUserAddressRequestDTO request) {

        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        UserAddress session = userAddressRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User address not found"));

        session.setAddressLine1(request.getAddressLine1());
        session.setHouseNumber(request.getHouseNumber());
        session.setCity(request.getCity());
        session.setState(request.getState());
        session.setPinCode(request.getPinCode());

        userAddressRepository.save(session);
    }
}
