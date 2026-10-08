package com.zivdah.auth.service;

import com.zivdah.auth.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface MpinService {
    Mono<MpinSetupResponseDTO> setup(Long userId, Instant authTime, MpinSetupRequestDTO request);
    Mono<LoginResponseDTO> login(MpinLoginRequestDTO request);
    Mono<Void> change(Long userId, MpinChangeRequestDTO request);
    Flux<MpinDeviceResponseDTO> getDevices(Long userId);
    Mono<Void> revokeDevice(Long userId, String deviceId);
}
