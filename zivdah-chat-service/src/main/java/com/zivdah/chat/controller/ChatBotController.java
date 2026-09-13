package com.zivdah.chat.controller;

import com.zivdah.chat.dto.ApiResponse;
import com.zivdah.chat.dto.BotConfirmRequestDto;
import com.zivdah.chat.dto.BotMessageRequestDto;
import com.zivdah.chat.dto.BotMessageResponseDto;
import com.zivdah.chat.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("/restful/v1/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatBotController {

    private final ChatbotService chatbotService;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    // The caller's own validated JWT — forwarded to a handful of downstream service calls that
    // require an authenticated caller (see ChatContext#bearerToken(), OrderServiceClient,
    // DeliveryServiceClient). Populated as the Authentication's credentials by
    // JwtAuthenticationFilter.
    private Mono<String> currentToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> (String) auth.getCredentials());
    }

    private Mono<Tuple2<Long, String>> currentUserIdAndToken() {
        return Mono.zip(currentUserId(), currentToken());
    }

    @PostMapping("/bot/message")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<BotMessageResponseDto>>> botMessage(
            @Valid @RequestBody BotMessageRequestDto request) {
        return currentUserIdAndToken()
                .flatMap(t -> chatbotService.handleBotMessage(t.getT1(), t.getT2(), request))
                .map(r -> ResponseEntity.ok(ApiResponse.<BotMessageResponseDto>builder()
                        .status("success").statusCode(200).message("Bot reply generated").data(r).build()));
    }

    @PostMapping("/bot/confirm")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<BotMessageResponseDto>>> botConfirm(
            @Valid @RequestBody BotConfirmRequestDto request) {
        return currentUserIdAndToken()
                .flatMap(t -> chatbotService.confirmAction(t.getT1(), t.getT2(), request))
                .map(r -> ResponseEntity.ok(ApiResponse.<BotMessageResponseDto>builder()
                        .status("success").statusCode(200).message("Confirmation processed").data(r).build()));
    }
}
