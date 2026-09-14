package com.zivdah.chat.controller;

import com.zivdah.chat.dto.ApiResponse;
import com.zivdah.chat.dto.ConversationResponseDto;
import com.zivdah.chat.dto.ConversationSummaryDto;
import com.zivdah.chat.dto.MessageResponseDto;
import com.zivdah.chat.dto.OrderContextResponseDto;
import com.zivdah.chat.dto.RatingRequestDto;
import com.zivdah.chat.dto.RatingResponseDto;
import com.zivdah.chat.dto.SendMessageRequestDto;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.enums.ConversationType;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import com.zivdah.chat.exception.ForbiddenOperationException;
import com.zivdah.chat.service.ConversationService;
import com.zivdah.chat.service.MessageService;
import com.zivdah.chat.service.OrderContextService;
import com.zivdah.chat.service.RatingService;
import com.zivdah.common.upload.CloudinaryUploadService;
import com.zivdah.common.upload.UploadCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/restful/v1/api/chat/conversations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConversationController {

    private final ConversationService conversationService;
    private final RatingService ratingService;
    private final OrderContextService orderContextService;
    private final MessageService messageService;
    private final CloudinaryUploadService cloudinaryUploadService;

    @Value("${cloudinary.folder}")
    private String cloudinaryFolder;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    // Forwarded to OrderContextService's delivery-service call — see ChatBotController's
    // identical helper for why (JwtAuthenticationFilter carries the raw token as credentials).
    private Mono<String> currentToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> (String) auth.getCredentials());
    }

    // Reads the caller's role straight off the authenticated Authentication's granted authorities
    // (JwtAuthenticationFilter populates exactly one: "ROLE_USER" or "ROLE_ADMIN"). Needed here
    // (unlike the USER-only/ADMIN-only endpoints below) because getConversation/getMessages are
    // hasAnyRole('USER','ADMIN') and behave differently per role — see
    // ConversationService#loadConversationForViewer.
    private Mono<String> currentRole() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .flatMapIterable(Authentication::getAuthorities)
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .next();
    }

    // Explicit "Talk to Human" button — same handoff mechanics as the bot's TALK_TO_HUMAN intent
    // path (ConversationService#requestHumanHandoff). Idempotent: a conversation already handed
    // off to HUMAN is returned as-is rather than re-transitioned/re-published.
    @PostMapping("/{id}/request-human")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<ConversationResponseDto>>> requestHuman(@PathVariable Long id) {
        return currentUserId()
                .flatMap(customerId -> conversationService.loadOwnedConversation(id, customerId))
                .flatMap(conversation -> conversation.getType() == ConversationType.HUMAN
                        ? Mono.just(conversation)
                        : conversationService.requestHumanHandoff(conversation))
                .map(conversation -> ResponseEntity.ok(ApiResponse.<ConversationResponseDto>builder()
                        .status("success").statusCode(200).message("Connecting you with a human agent")
                        .data(toDto(conversation)).build()));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ConversationResponseDto>>> accept(@PathVariable Long id) {
        return currentUserId()
                .flatMap(agentId -> conversationService.acceptConversation(id, agentId))
                .map(conversation -> ResponseEntity.ok(ApiResponse.<ConversationResponseDto>builder()
                        .status("success").statusCode(200).message("Conversation accepted")
                        .data(toDto(conversation)).build()));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ConversationResponseDto>>> close(@PathVariable Long id) {
        return currentUserId()
                .flatMap(agentId -> conversationService.closeConversation(id, agentId))
                .map(conversation -> ResponseEntity.ok(ApiResponse.<ConversationResponseDto>builder()
                        .status("success").statusCode(200).message("Conversation closed")
                        .data(toDto(conversation)).build()));
    }

    // Customer self-service counterpart to close() above — Assistant (BOT-type) conversations
    // only. The existing close() endpoint stays admin/agent-only and is untouched; a HUMAN
    // conversation can still only be ended by its assigned agent. See
    // ConversationService#endOwnBotConversation for the BOT-only / ownership enforcement.
    @PutMapping("/{id}/end")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<ConversationResponseDto>>> end(@PathVariable Long id) {
        return currentUserId()
                .flatMap(customerId -> conversationService.endOwnBotConversation(id, customerId))
                .map(conversation -> ResponseEntity.ok(ApiResponse.<ConversationResponseDto>builder()
                        .status("success").statusCode(200).message("Chat ended")
                        .data(toDto(conversation)).build()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ConversationResponseDto>>> getConversation(@PathVariable Long id) {
        return currentUserId().zipWith(currentRole())
                .flatMap(caller -> conversationService.loadConversationForViewer(id, caller.getT1(), caller.getT2()))
                .map(conversation -> ResponseEntity.ok(ApiResponse.<ConversationResponseDto>builder()
                        .status("success").statusCode(200).message("Conversation loaded")
                        .data(toDto(conversation)).build()));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<MessageResponseDto>>>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long afterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return currentUserId().zipWith(currentRole())
                .flatMapMany(caller -> conversationService.loadMessages(
                        id, caller.getT1(), caller.getT2(), afterId, PageRequest.of(page, size)))
                .map(ConversationController::toDto)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<MessageResponseDto>>builder()
                        .status("success").statusCode(200).message("Messages loaded")
                        .data(list).build()));
    }

    // REST fallback for sending a message — shares MessageService#sendMessage (persist +
    // broadcast) with ChatWebSocketHandler, so the two paths never drift. USER may only use this
    // once the conversation has actually been handed off to a human; ADMIN only if assigned.
    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<MessageResponseDto>>> sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendMessageRequestDto request) {
        return currentUserId().zipWith(currentRole())
                .flatMap(caller -> conversationService.loadConversationForViewer(id, caller.getT1(), caller.getT2())
                        .flatMap(conversation -> requireSenderAllowed(conversation, caller))
                        .flatMap(conversation -> messageService.sendMessage(id, caller.getT1(),
                                senderTypeFor(caller.getT2()), parseMessageType(request.getMessageType()),
                                request.getMessage(), request.getAttachmentUrl())))
                .map(ConversationController::toDto)
                .map(dto -> ResponseEntity.ok(ApiResponse.<MessageResponseDto>builder()
                        .status("success").statusCode(200).message("Message sent").data(dto).build()));
    }

    // Paperclip upload — validated/stored via the shared CloudinaryUploadService (zivdah-common),
    // same one zivdah-product-service already uses; no new file-storage code. Returns the full
    // created message record (not a bare URL) so the frontend needs no second round-trip.
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<MessageResponseDto>>> uploadAttachment(
            @PathVariable Long id, @RequestPart("file") FilePart file) {
        return currentUserId().zipWith(currentRole())
                .flatMap(caller -> conversationService.loadConversationForViewer(id, caller.getT1(), caller.getT2())
                        .flatMap(conversation -> requireSenderAllowed(conversation, caller))
                        .flatMap(conversation -> {
                            UploadCategory category = categoryFor(file);
                            return cloudinaryUploadService.upload(file, category, cloudinaryFolder + "/" + id)
                                    .flatMap(result -> messageService.sendMessage(id, caller.getT1(),
                                            senderTypeFor(caller.getT2()),
                                            category == UploadCategory.IMAGE ? MessageType.IMAGE : MessageType.FILE,
                                            file.filename(), result.getSecureUrl()));
                        }))
                .map(ConversationController::toDto)
                .map(dto -> ResponseEntity.ok(ApiResponse.<MessageResponseDto>builder()
                        .status("success").statusCode(200).message("Attachment sent").data(dto).build()));
    }

    private Mono<ChatConversation> requireSenderAllowed(ChatConversation conversation, Tuple2<Long, String> caller) {
        if ("ADMIN".equals(caller.getT2())) {
            if (conversation.getAssignedAgentId() == null || !conversation.getAssignedAgentId().equals(caller.getT1())) {
                return Mono.error(new ForbiddenOperationException("Only the assigned agent can send messages here"));
            }
        } else if (conversation.getType() != ConversationType.HUMAN) {
            return Mono.error(new ForbiddenOperationException(
                    "This conversation hasn't been handed off to a human agent yet"));
        }
        return Mono.just(conversation);
    }

    private SenderType senderTypeFor(String role) {
        return "ADMIN".equals(role) ? SenderType.AGENT : SenderType.CUSTOMER;
    }

    private MessageType parseMessageType(String raw) {
        if (raw == null) {
            return MessageType.TEXT;
        }
        try {
            return MessageType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MessageType.TEXT;
        }
    }

    private UploadCategory categoryFor(FilePart file) {
        var mediaType = file.headers().getContentType();
        String contentType = mediaType != null ? mediaType.toString() : "";
        if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return UploadCategory.IMAGE;
        }
        if (contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return UploadCategory.VIDEO;
        }
        return UploadCategory.DOCUMENT;
    }

    // UploadCategory#validate throws ResponseStatusException directly (a jakarta ResponseStatusException,
    // not one of this service's own exception types) — map it to the standard ApiResponse envelope
    // rather than letting it fall through to the generic 500 handler.
    @org.springframework.web.bind.annotation.ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiResponse.builder()
                .status("error").statusCode(status.value()).message(ex.getReason()).data(null).build());
    }

    // Section 10/12's agent info panel.
    @GetMapping("/{id}/order-context")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<OrderContextResponseDto>>> getOrderContext(@PathVariable Long id) {
        return currentToken()
                .flatMap(token -> orderContextService.getOrderContext(id, token))
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderContextResponseDto>builder()
                        .status("success").statusCode(200).data(r).build()));
    }

    // --- Phase 6: list/queue/search views ---

    @GetMapping("/waiting")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<ConversationSummaryDto>>>> getWaiting(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return conversationService.getWaiting(PageRequest.of(page, size))
                .collectList()
                .map(ConversationController::summaryOk);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<ConversationSummaryDto>>>> getMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return currentUserId()
                .flatMapMany(agentId -> conversationService.getMine(agentId, PageRequest.of(page, size)))
                .collectList()
                .map(ConversationController::summaryOk);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<ConversationSummaryDto>>>> getAll(
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return conversationService.getAll(status, PageRequest.of(page, size))
                .collectList()
                .map(ConversationController::summaryOk);
    }

    @GetMapping("/customer/me")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<List<ConversationSummaryDto>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return currentUserId()
                .flatMapMany(customerId -> conversationService.getCustomerConversations(customerId, PageRequest.of(page, size)))
                .collectList()
                .map(ConversationController::summaryOk);
    }

    // Structured filters only — see ConversationSearchRepository's javadoc for the free-text
    // message-search gap.
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<ConversationSummaryDto>>>> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return conversationService.search(customerId, orderId, status, agentId, from, to, PageRequest.of(page, size))
                .collectList()
                .map(ConversationController::summaryOk);
    }

    private static ResponseEntity<ApiResponse<List<ConversationSummaryDto>>> summaryOk(List<ConversationSummaryDto> list) {
        return ResponseEntity.ok(ApiResponse.<List<ConversationSummaryDto>>builder()
                .status("success").statusCode(200).data(list).build());
    }

    // Section 20 — shown when a HUMAN conversation closes (SYSTEM/CLOSED frame over the socket).
    @PostMapping("/{id}/rating")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<RatingResponseDto>>> submitRating(
            @PathVariable Long id, @Valid @RequestBody RatingRequestDto request) {
        return currentUserId()
                .flatMap(customerId -> ratingService.submitRating(id, customerId, request))
                .map(r -> ResponseEntity.ok(ApiResponse.<RatingResponseDto>builder()
                        .status("success").statusCode(200).message("Thanks for your feedback").data(r).build()));
    }

    private static ConversationResponseDto toDto(ChatConversation c) {
        return ConversationResponseDto.builder()
                .id(c.getId())
                .customerId(c.getCustomerId())
                .type(c.getType() != null ? c.getType().name() : null)
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .assignedAgentId(c.getAssignedAgentId())
                .orderId(c.getOrderId())
                .topic(c.getTopic())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .closedAt(c.getClosedAt())
                .build();
    }

    private static MessageResponseDto toDto(ChatMessage m) {
        return MessageResponseDto.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .senderType(m.getSenderType() != null ? m.getSenderType().name() : null)
                .messageType(m.getMessageType() != null ? m.getMessageType().name() : null)
                .message(m.getMessage())
                .attachmentUrl(m.getAttachmentUrl())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
