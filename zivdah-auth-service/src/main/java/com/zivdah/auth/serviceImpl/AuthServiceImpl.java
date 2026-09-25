package com.zivdah.auth.serviceImpl;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.DeviceToken;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.entity.UserSession;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.repository.DeviceTokenRepository;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.repository.UserSessionRepository;
import com.zivdah.auth.security.JwtTokenProvider;
import com.zivdah.auth.security.RefreshTokenService;
import com.zivdah.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final DeviceTokenRepository deviceTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final Resend resend;

    @Value("${resend.from-email}")
    private String fromEmail;

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
                    // Mobile OTP stays the static demo value until a real SMS provider is wired
                    // up; email OTP is real now, sent the same way forget-password sends its.
                    String emailOtp = generateOtp();
                    UserEntity user = UserEntity.builder()
                            .name(request.getName())
                            .email(request.getEmail())
                            .mobile(request.getMobile())
                            .role(role)
                            .password(encodedPassword)
                            .active(false)
                            .mobileOtp(STATIC_OTP)
                            .emailOtp(emailOtp)
                            .otpGeneratedAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(user)
                            .flatMap(saved -> sendOtpEmail(saved.getEmail(), emailOtp,
                                    "Verify your Zivdah account").thenReturn(saved));
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
        if (hasMobile && !hasEmail) {
            return Mono.error(new RuntimeException(
                    "Mobile login uses OTP. Call /restful/v1/api/auth/send-otp then /verify-otp instead."));
        }

        Mono<UserEntity> userMono = userRepository.findByEmail(request.getEmail());

        return userMono
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new RuntimeException("Account is deactivated"));
                    }
                    return buildLoginResponse(user, null);
                });
    }

    @Override
    public Mono<LoginResponseDTO> refreshToken(RefreshTokenRequestDTO request) {
        return refreshTokenService.rotate(request.getRefreshToken())
                .map(rotation -> toLoginResponse(rotation.user(), rotation.refreshToken()));
    }

    // Shared by every flow that signs a user in: a fresh access token plus a new refresh token
    // (familyId null = new rotation chain for this login).
    private Mono<LoginResponseDTO> buildLoginResponse(UserEntity user, String familyId) {
        return refreshTokenService.issue(user.getId(), familyId)
                .map(refreshToken -> toLoginResponse(user, refreshToken));
    }

    private LoginResponseDTO toLoginResponse(UserEntity user, String refreshToken) {
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getMobile(), user.getRole().name());
        return LoginResponseDTO.builder()
                .id(user.getId())
                .mobile(user.getMobile())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .refreshExpiresIn(refreshTokenService.getRefreshTokenExpirySeconds())
                .build();
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
    public Mono<LoginResponseDTO> verifyOtp(VerifyLoginOtpDTO request) {
        return userRepository.findByMobile(request.getMobile())
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!STATIC_OTP.equals(request.getOtp())) {
                        return Mono.error(new RuntimeException("Invalid OTP"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new RuntimeException("Account is deactivated"));
                    }
                    return buildLoginResponse(user, null)
                            .flatMap(resp -> saveSession(user, resp.getAccessToken(), request.getDeviceToken())
                                    .thenReturn(resp));
                });
    }

    // user_sessions bookkeeping + device-token registration done by both OTP login flows.
    private Mono<Void> saveSession(UserEntity user, String accessToken, String deviceToken) {
        Mono<UserSession> sessionMono = userSessionRepository.findByUserId(user.getId())
                .defaultIfEmpty(UserSession.builder().userId(user.getId()).build())
                .flatMap(session -> {
                    session.setToken(accessToken);
                    session.setDeviceToken(deviceToken);
                    session.setCreatedAt(LocalDateTime.now());
                    return userSessionRepository.save(session);
                });
        return sessionMono.then(registerDeviceToken(user.getId(), user.getRole().name(), "WEB", deviceToken));
    }

    @Override
    public Flux<AuthUserResponseDTO> getAllUsers() {
        return userRepository.findAllByOrderByIdDesc().map(this::toAuthUserResponseDTO);
    }

    @Override
    public Flux<AuthUserResponseDTO> getUsersByRole(Role role) {
        return userRepository.findByRole(role).map(this::toAuthUserResponseDTO);
    }

    private AuthUserResponseDTO toAuthUserResponseDTO(UserEntity user) {
        return AuthUserResponseDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    @Override
    public Flux<Long> getAdminUserIds() {
        return userRepository.findByRole(Role.ADMIN).map(UserEntity::getId);
    }

    @Override
    public Mono<Void> registerDeviceToken(Long userId, String role, String deviceType, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return Mono.empty();
        }
        String normalizedDeviceType = (deviceType == null || deviceType.isBlank())
                ? "WEB" : deviceType.toUpperCase();
        return deviceTokenRepository.findByFcmToken(fcmToken)
                .defaultIfEmpty(DeviceToken.builder().fcmToken(fcmToken).createdAt(LocalDateTime.now()).build())
                .flatMap(existing -> {
                    existing.setUserId(userId);
                    existing.setUserRole(role);
                    existing.setDeviceType(normalizedDeviceType);
                    existing.setActive(true);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return deviceTokenRepository.save(existing);
                })
                .then();
    }

    @Override
    public Flux<String> getActiveDeviceTokens(Long userId) {
        return deviceTokenRepository.findByUserIdAndIsActiveTrue(userId).map(DeviceToken::getFcmToken);
    }

    @Override
    public Mono<Void> deactivateDeviceToken(String fcmToken) {
        return deviceTokenRepository.findByFcmToken(fcmToken)
                .flatMap(existing -> {
                    existing.setActive(false);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return deviceTokenRepository.save(existing);
                })
                .then();
    }

    @Override
    public Mono<UserResponseDTO> updateProfile(Long userId, UpdateUserProfileDTO dto) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.setName(dto.getName());
                    // Optional and independent of name — a field left out of the request (null)
                    // keeps whatever is already on file rather than being wiped.
                    if (dto.getBankAccountNumber() != null) user.setBankAccountNumber(dto.getBankAccountNumber());
                    if (dto.getBankIfscCode() != null) user.setBankIfscCode(dto.getBankIfscCode());
                    if (dto.getUpiVpa() != null) user.setUpiVpa(dto.getUpiVpa());
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
    public Mono<BankDetailsResponseDTO> getBankDetails(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .map(user -> BankDetailsResponseDTO.builder()
                        .bankAccountNumber(user.getBankAccountNumber())
                        .bankIfscCode(user.getBankIfscCode())
                        .upiVpa(user.getUpiVpa())
                        .build());
    }

    @Override
    public Mono<Boolean> sendPasswordResetOtp(String email) {
        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) return Mono.just(false);
                    String otp = generateOtp();
                    otpStorage.put(email, otp);
                    return sendOtpEmail(email, otp, "Password Reset OTP").thenReturn(true);
                });
    }

    private Mono<Void> sendOtpEmail(String toEmail, String otp, String subject) {
        return Mono.fromCallable(() -> {
                    CreateEmailOptions params = CreateEmailOptions.builder()
                            .from(fromEmail)
                            .to(toEmail)
                            .subject(subject)
                            .text("Your OTP: " + otp + " (valid for 10 minutes)")
                            .build();
                    return resend.emails().send(params);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to send OTP email to {}", toEmail, e))
                .then();
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
                            // New password = sign out every device holding a refresh token.
                            .flatMap(saved -> refreshTokenService.revokeAll(saved.getId()).thenReturn(saved))
                            .doOnSuccess(u -> otpStorage.remove(request.getEmail()))
                            .thenReturn(ResetPasswordResponseDTO.builder()
                                    .status("success").message("Password reset successfully").build());
                });
    }

    @Override
    public Mono<LoginResponseDTO> verifyRegistrationOtp(VerifyOtpDTO request) {
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

                    return userRepository.save(user)
                            .flatMap(saved -> buildLoginResponse(saved, null)
                                    .flatMap(resp -> saveSession(saved, resp.getAccessToken(), request.getDeviceToken())
                                            .thenReturn(resp)));
                });
    }

    @Override
    public Mono<Void> logout(Long userId, String fcmToken, String refreshToken) {
        if (userId == null) {
            // Access token already expired: possession of the refresh token is enough to
            // revoke it (and this device's push token), but not to touch the user's session.
            Mono<Void> revoke = refreshTokenService.revoke(refreshToken);
            return (fcmToken == null || fcmToken.isBlank()) ? revoke : revoke.then(deactivateDeviceToken(fcmToken));
        }
        Mono<Void> revokeRefresh = (refreshToken == null || refreshToken.isBlank())
                ? refreshTokenService.revokeAll(userId)
                : refreshTokenService.revoke(refreshToken);
        Mono<Void> clearSession = revokeRefresh.then(userSessionRepository.deleteByUserId(userId));
        if (fcmToken == null || fcmToken.isBlank()) {
            return clearSession;
        }
        return clearSession.then(deactivateDeviceToken(fcmToken));
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
                .then(refreshTokenService.revokeAll(userId))
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

    @Override
    public Mono<UserStatsResponseDTO> getUserStats() {
        return Mono.zip(
                userRepository.count(),
                userRepository.countByRole(Role.ADMIN),
                userRepository.countByRole(Role.VENDOR),
                userRepository.countByRole(Role.USER)
        ).map(t -> UserStatsResponseDTO.builder()
                .totalUsers(t.getT1())
                .totalAdmins(t.getT2())
                .totalVendors(t.getT3())
                .totalCustomers(t.getT4())
                .build());
    }

    private String generateOtp() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}
