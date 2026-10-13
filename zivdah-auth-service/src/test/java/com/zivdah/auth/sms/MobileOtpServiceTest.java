package com.zivdah.auth.sms;

import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.exception.OtpException;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.sms.SmsOtpProvider.OtpCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileOtpServiceTest {

    private SmsOtpProvider provider;
    private UserRepository users;
    private MobileOtpService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        provider = mock(SmsOtpProvider.class);
        users = mock(UserRepository.class);
        service = new MobileOtpService(provider, users);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "resendCooldown", Duration.ofSeconds(60));
        user = UserEntity.builder().id(7L).mobile("9876543210").mobileOtpRef("logid-1").build();

        when(users.storeOtpRef(anyLong(), anyString())).thenReturn(Mono.just(1));
        when(users.releaseOtpSend(anyLong())).thenReturn(Mono.just(1));
        when(users.clearOtpRef(anyLong(), anyString())).thenReturn(Mono.just(1));
    }

    private static void assertOtpError(Throwable e, HttpStatus status, String messageStart) {
        assertThat(e).isInstanceOf(OtpException.class);
        assertThat(((OtpException) e).getStatus()).isEqualTo(status);
        assertThat(e.getMessage()).startsWith(messageStart);
    }

    @Test
    void sendStoresTheProvidersReference() {
        when(users.claimOtpSend(eq(7L), any(), any())).thenReturn(Mono.just(1));
        when(provider.send("9876543210")).thenReturn(Mono.just("logid-2"));

        StepVerifier.create(service.send(user)).verifyComplete();
        verify(users).storeOtpRef(7L, "logid-2");
    }

    @Test
    void sendWithinCooldownIs429AndSendsNoSms() {
        user.setOtpGeneratedAt(LocalDateTime.now().minusSeconds(20));
        when(users.claimOtpSend(eq(7L), any(), any())).thenReturn(Mono.just(0));

        StepVerifier.create(service.send(user))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.TOO_MANY_REQUESTS, "Please wait 4"));
        verifyNoInteractions(provider);
    }

    @Test
    void failedSendReleasesTheCooldown() {
        when(users.claimOtpSend(eq(7L), any(), any())).thenReturn(Mono.just(1));
        when(provider.send(anyString())).thenReturn(Mono.error(new OtpException(HttpStatus.SERVICE_UNAVAILABLE, "down")));

        StepVerifier.create(service.send(user))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.SERVICE_UNAVAILABLE, "down"));
        verify(users).releaseOtpSend(7L);
        verify(users, never()).storeOtpRef(anyLong(), anyString());
    }

    @Test
    void correctOtpIsConsumed() {
        when(users.reserveOtpAttempt(7L, "logid-1", 5)).thenReturn(Mono.just(1));
        when(provider.verify("9876543210", "logid-1", "482917")).thenReturn(Mono.just(OtpCheckResult.VALID));

        StepVerifier.create(service.verify(user, "482917")).verifyComplete();
        verify(users).clearOtpRef(7L, "logid-1");
    }

    @Test
    void wrongOtpIsInvalidAndKeepsTheReference() {
        when(users.reserveOtpAttempt(7L, "logid-1", 5)).thenReturn(Mono.just(1));
        when(provider.verify(anyString(), anyString(), anyString())).thenReturn(Mono.just(OtpCheckResult.INVALID));

        StepVerifier.create(service.verify(user, "000001"))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.BAD_REQUEST, MobileOtpService.INVALID));
        verify(users, never()).clearOtpRef(anyLong(), anyString());
    }

    @Test
    void noAttemptsLeftNeverReachesTheProvider() {
        when(users.reserveOtpAttempt(7L, "logid-1", 5)).thenReturn(Mono.just(0));

        StepVerifier.create(service.verify(user, "482917"))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.BAD_REQUEST, MobileOtpService.REQUEST_NEW));
        verifyNoInteractions(provider);
        verify(users).clearOtpRef(7L, "logid-1");
    }

    @Test
    void expiredOtpIsDropped() {
        when(users.reserveOtpAttempt(7L, "logid-1", 5)).thenReturn(Mono.just(1));
        when(provider.verify(anyString(), anyString(), anyString())).thenReturn(Mono.just(OtpCheckResult.EXPIRED));

        StepVerifier.create(service.verify(user, "482917"))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.BAD_REQUEST, MobileOtpService.REQUEST_NEW));
        verify(users).clearOtpRef(7L, "logid-1");
    }

    @Test
    void noOutstandingOtpAsksForANewOne() {
        user.setMobileOtpRef(null);

        StepVerifier.create(service.verify(user, "482917"))
                .verifyErrorSatisfies(e -> assertOtpError(e, HttpStatus.BAD_REQUEST, MobileOtpService.REQUEST_NEW));
        verifyNoInteractions(provider);
    }
}
