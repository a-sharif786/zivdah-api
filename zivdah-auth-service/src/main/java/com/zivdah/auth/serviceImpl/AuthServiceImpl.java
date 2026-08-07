package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.entity.UserSession;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.repository.UserSessionRepository;
import com.zivdah.auth.security.JwtTokenProvider;
import com.zivdah.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JavaMailSender mailSender;

    // In-memory OTP store (use Redis in production)
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();
    private static final String STATIC_OTP = "123456";

    @Override
    public Mono<Void> register(RegisterRequestDTO request) {
        if (request.getRole() == Role.ADMIN) {
            return Mono.error(new RuntimeException("Cannot self-register as ADMIN"));
        }
        Role role = request.getRole() == null ? Role.USER : request.getRole();
        return Mono.zip(
                        userRepository.existsByEmail(request.getEmail()),
                        userRepository.existsByMobile(request.getMobile())
                )
                .flatMap(tuple -> {
                    boolean emailExists = tuple.getT1();
                    boolean mobileExists = tuple.getT2();
                    if (emailExists && mobileExists) return Mono.error(new RuntimeException("Both email and mobile already registered"));
                    if (emailExists) return Mono.error(new RuntimeException("Email already registered"));
                    if (mobileExists) return Mono.error(new RuntimeException("Mobile already registered"));
                    return Mono.empty();
                })
                .then(Mono.fromCallable(() -> passwordEncoder.encode(request.getPassword()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(encodedPassword -> {
                    UserEntity user = UserEntity.builder()
                            .name(request.getName())
                            .email(request.getEmail())
                            .mobile(request.getMobile())
                            .role(role)
                            .password(encodedPassword)
                            .active(false)
                            .mobileOtp(STATIC_OTP)
                            .emailOtp(STATIC_OTP)
                            .otpGeneratedAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(user);
                })
                .doOnSuccess(user -> log.info("User registered: {}", user.getMobile()))
                .then();
    }

    @Override
    public Mono<LoginResponseDTO> login(LoginRequestDTO request) {
        boolean hasMobile = request.getMobile() != null && !request.getMobile().isBlank();
        boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();
        if (!hasMobile && !hasEmail) {
            return Mono.error(new RuntimeException("Mobile or email is required"));
        }

        Mono<UserEntity> userMono = hasMobile
                ? userRepository.findByMobile(request.getMobile())
                : userRepository.findByEmail(request.getEmail());

        return userMono
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new RuntimeException("Account is deactivated"));
                    }
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getMobile(), user.getRole().name());
                    return Mono.just(new LoginResponseDTO(user.getId(), user.getMobile(), user.getName(),
                            user.getEmail(), user.getRole(), token));
                });
    }

    @Override
    public Mono<UserEntity> getUserByMobile(String mobile) {
        return userRepository.findByMobile(mobile)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found with mobile: " + mobile)));
    }

    @Override
    public Mono<UserEntity> getUserById(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found with id: " + userId)));
    }

    @Override
    public Mono<Void> sendOtp(String mobile) {
        return userRepository.findByMobile(mobile)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setMobileOtp(STATIC_OTP);
                    user.setOtpGeneratedAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .doOnSuccess(u -> log.info("OTP {} sent to {}", STATIC_OTP, mobile))
                .then();
    }

    @Override
    public Mono<String> verifyOtp(VerifyLoginOtpDTO request) {
        return userRepository.findByMobile(request.getMobile())
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!STATIC_OTP.equals(request.getOtp())) {
                        return Mono.error(new RuntimeException("Invalid OTP"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new RuntimeException("Account is deactivated"));
                    }
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getMobile(), user.getRole().name());
                    return userSessionRepository.findByUserId(user.getId())
                            .defaultIfEmpty(UserSession.builder().userId(user.getId()).build())
                            .flatMap(session -> {
                                session.setToken(token);
                                session.setDeviceToken(request.getDeviceToken());
                                session.setCreatedAt(LocalDateTime.now());
                                return userSessionRepository.save(session);
                            })
                            .thenReturn(token);
                });
    }

    @Override
    public Flux<AuthUserResponseDTO> getAllUsers() {
        return userRepository.findAll()
                .map(user -> AuthUserResponseDTO.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .mobile(user.getMobile())
                        .role(user.getRole())
                        .active(user.isActive())
                        .build());
    }

    @Override
    public Mono<UserResponseDTO> updateProfile(Long userId, UpdateUserProfileDTO dto) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setName(dto.getName());
                    return userRepository.save(user);
                })
                .map(user -> UserResponseDTO.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .mobile(user.getMobile())
                        .role(user.getRole().name())
                        .build());
    }

    @Override
    public Mono<Boolean> sendPasswordResetOtp(String email) {
        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) return Mono.just(false);
                    String otp = generateOtp();
                    otpStorage.put(email, otp);
                    return Mono.fromRunnable(() -> {
                                SimpleMailMessage msg = new SimpleMailMessage();
                                msg.setTo(email);
                                msg.setSubject("Password Reset OTP");
                                msg.setText("Your OTP: " + otp + " (valid for 10 minutes)");
                                mailSender.send(msg);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenReturn(true);
                });
    }

    @Override
    public Mono<ResetPasswordResponseDTO> resetPassword(ResetPasswordDTO request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new RuntimeException("User not found with email: " + request.getEmail())))
                .flatMap(user -> {
                    String storedOtp = otpStorage.get(request.getEmail());
                    if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
                        return Mono.just(ResetPasswordResponseDTO.builder()
                                .status("failure").message("Invalid OTP").build());
                    }
                    return Mono.fromCallable(() -> passwordEncoder.encode(request.getNewPassword()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(encoded -> {
                                user.setPassword(encoded);
                                return userRepository.save(user);
                            })
                            .doOnSuccess(u -> otpStorage.remove(request.getEmail()))
                            .thenReturn(ResetPasswordResponseDTO.builder()
                                    .status("success").message("Password reset successfully").build());
                });
    }

    @Override
    public Mono<String> verifyRegistrationOtp(VerifyOtpDTO request) {
        return userRepository.findByMobile(request.getMobile())
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!user.getEmail().equals(request.getEmail()))
                        return Mono.error(new RuntimeException("Email does not match"));
                    if (!request.getMobileOtp().equals(user.getMobileOtp()))
                        return Mono.error(new RuntimeException("Invalid mobile OTP"));
                    if (!request.getEmailOtp().equals(user.getEmailOtp()))
                        return Mono.error(new RuntimeException("Invalid email OTP"));

                    user.setActive(true);
                    user.setMobileOtp(null);
                    user.setEmailOtp(null);
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getMobile(), user.getRole().name());

                    return userRepository.save(user)
                            .flatMap(saved ->
                                    userSessionRepository.findByUserId(saved.getId())
                                            .defaultIfEmpty(UserSession.builder().userId(saved.getId()).build())
                                            .flatMap(session -> {
                                                session.setToken(token);
                                                session.setDeviceToken(request.getDeviceToken());
                                                session.setCreatedAt(LocalDateTime.now());
                                                return userSessionRepository.save(session);
                                            })
                            )
                            .thenReturn(token);
                });
    }

    @Override
    public Mono<Void> logout(Long userId) {
        return userSessionRepository.deleteByUserId(userId);
    }

    @Override
    public Mono<UserResponseDTO> updateRole(Long userId, Role role) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setRole(role);
                    return userRepository.save(user);
                })
                .map(user -> UserResponseDTO.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .mobile(user.getMobile())
                        .role(user.getRole().name())
                        .build());
    }

    @Override
    public Mono<Void> deactivateAccount(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user);
                })
                .then(userSessionRepository.deleteByUserId(userId));
    }

    @Override
    public Mono<Void> activateAccount(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user);
                })
                .then();
    }

    private String generateOtp() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}
