package com.zivdah.auth.service;

import com.zivdah.auth.dto.LoginRequestDTO;
import com.zivdah.auth.dto.RegisterRequestDTO;
import com.zivdah.auth.dto.VerifyOtpDTO;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.entity.UserSession;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.repository.UserSessionRepository;
import com.zivdah.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private static  String STATIC_OTP = "123456"; // static OTP

    @Autowired
    private UserSessionRepository userSessionRepository;

    public void register(RegisterRequestDTO request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        if (userRepository.existsByMobile(request.getMobile())) {
            throw new RuntimeException("Mobile already exists");
        }
        UserEntity user = UserEntity.builder()
                .name(request.getName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .role(Role.USER)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
    }


    public String  loginWithMobile(LoginRequestDTO request) {
        UserEntity userEntity = userRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!STATIC_OTP.equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        return jwtUtil.generateToken(userEntity.getMobile());


    }

    public UserEntity getUserByMobile(String mobile) {
        return userRepository.findByMobile(mobile)
                .orElseThrow(() -> new RuntimeException("User not found with mobile: " + mobile));
    }



    // Step 1: Send OTP (for now static)
    public void sendOtp(String mobile) {
        // Check if user exists
        userRepository.findByMobile(mobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // In production, generate/send OTP via SMS here
        System.out.println("Sending OTP " + STATIC_OTP + " to " + mobile);
    }

    // Step 2: Verify OTP
    public String verifyOtp(VerifyOtpDTO request) {
        UserEntity user = userRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!STATIC_OTP.equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getMobile());

        Optional<UserSession> existingSession =
                userSessionRepository.findByUserId(
                        user.getId()
                );

         log.info("Parente>>>>>>"+existingSession.isPresent());

        UserSession session;
        if (existingSession.isPresent()) {
            // UPDATE
            session = existingSession.get();
            session.setToken(token);
            session.setDeviceToken(request.getDeviceToken());
            session.setCreatedAt(LocalDateTime.now());
        } else {
            // INSERT
            session = UserSession.builder()
                    .userId(user.getId())
                    .token(token)
                    .deviceToken(request.getDeviceToken())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        userSessionRepository.save(session);

        return token;
    }



}