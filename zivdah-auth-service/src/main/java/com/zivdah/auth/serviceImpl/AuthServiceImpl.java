package com.zivdah.auth.service.impl;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.entity.UserSession;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.repository.UserSessionRepository;
import com.zivdah.auth.security.JwtUtil;
import com.zivdah.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JavaMailSender mailSender;

    // In-memory storage for OTPs (for demo, use DB/Redis in production)
    private final Map<String, String> otpStorage = new HashMap<>();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;

    // ❌ @Autowired static is wrong
    private static final String STATIC_OTP = "123456";

    @Override
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

    @Override
    public String loginWithMobile(LoginRequestDTO request) {

        UserEntity userEntity = userRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!STATIC_OTP.equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        return jwtUtil.generateToken(userEntity.getMobile());
    }

    @Override
    public UserEntity getUserByMobile(String mobile) {
        return userRepository.findByMobile(mobile)
                .orElseThrow(() ->
                        new RuntimeException("User not found with mobile: " + mobile));
    }

    @Override
    public void sendOtp(String mobile) {

        userRepository.findByMobile(mobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // In real system: send OTP via SMS provider
        log.info("Sending OTP {} to {}", STATIC_OTP, mobile);
    }

    @Override
    public String verifyOtp(VerifyOtpDTO request) {

        UserEntity user = userRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!STATIC_OTP.equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        String token = jwtUtil.generateToken(user.getMobile());

        Optional<UserSession> existingSession =
                userSessionRepository.findByUserId(user.getId());

        UserSession session;
        if (existingSession.isPresent()) {
            session = existingSession.get();
            session.setToken(token);
            session.setDeviceToken(request.getDeviceToken());
            session.setCreatedAt(LocalDateTime.now());
        } else {
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


    @Override
    public List<AuthUserResponseDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> AuthUserResponseDTO.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .mobile(user.getMobile())
                        .role(user.getRole())
                        .build())
                .toList();
    }



    @Override
    public UserResponseDTO updateProfile(String mobile, UpdateUserProfileDTO dto) {

        UserEntity user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(dto.getName());
//      user.setEmail(dto.getEmail());

        UserEntity saved = userRepository.save(user);

        return UserResponseDTO.builder()
                .userId(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .mobile(saved.getMobile())
                .role(saved.getRole().name())
                .build();
    }




    @Override
    public boolean sendPasswordResetOtp(String email) {

            // 1️⃣ Check if email exists
            boolean exists = userRepository.existsByEmail(email);
            if (!exists) {
                return false; // Email not found
            }

            // 2️⃣ Generate OTP
            String otp = generateOtp();

            // 3️⃣ Store OTP temporarily (in real-world, expire after X minutes)
            otpStorage.put(email, otp);

            // 4️⃣ Send email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Your Password Reset OTP");
            message.setText("Your OTP for password reset is: " + otp + "\nThis OTP is valid for 10 minutes.");

            mailSender.send(message);

            return true; // Email sent
        }

        // Generate 6-digit numeric OTP
        private String generateOtp() {
            Random random = new Random();
            int number = 100000 + random.nextInt(900000);
            return String.valueOf(number);
        }

        // Optional: Validate OTP for reset
        public boolean validateOtp(String email, String otp) {
            return otpStorage.containsKey(email) && otpStorage.get(email).equals(otp);
        }







    @Override
    public ResetPasswordResponseDTO resetPassword(ResetPasswordDTO request) {

        // 1️⃣ Find user by email
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        // 2️⃣ Verify OTP (for simplicity, static OTP)
        if (!"123456".equals(request.getOtp())) {
            return ResetPasswordResponseDTO.builder()
                    .status("failure")
                    .message("Invalid OTP")
                    .build();
        }

        // 3️⃣ Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4️⃣ Return response
        return ResetPasswordResponseDTO.builder()
                .status("success")
                .message("Password reset successfully")
                .build();
    }


}
