package com.zivdah.chat.controller;

import com.zivdah.chat.dto.ApiResponse;
import com.zivdah.chat.dto.SupportAgentRequestDto;
import com.zivdah.chat.dto.SupportAgentResponseDto;
import com.zivdah.chat.service.SupportAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

// Section 11's "Support Agents" roster page — CRUD over which ADMIN accounts act as chat agents
// (see the plan's role decision: no distinct SUPPORT_AGENT role, just an ADMIN-scoped roster).
@RestController
@RequestMapping("/restful/v1/api/chat/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class SupportAgentController {

    private final SupportAgentService supportAgentService;

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<SupportAgentResponseDto>>>> getAgents() {
        return supportAgentService.getAgents()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<SupportAgentResponseDto>>builder()
                        .status("success").statusCode(200).data(list).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<SupportAgentResponseDto>>> addAgent(
            @Valid @RequestBody SupportAgentRequestDto request) {
        return supportAgentService.addAgent(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.<SupportAgentResponseDto>builder()
                                .status("success").statusCode(201).message("Support agent added").data(r).build()));
    }

    @PutMapping("/{userId}/toggle")
    public Mono<ResponseEntity<ApiResponse<SupportAgentResponseDto>>> toggleActive(@PathVariable Long userId) {
        return supportAgentService.toggleActive(userId)
                .map(r -> ResponseEntity.ok(ApiResponse.<SupportAgentResponseDto>builder()
                        .status("success").statusCode(200).message("Support agent updated").data(r).build()));
    }
}
