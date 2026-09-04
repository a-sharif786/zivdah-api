package com.zivdah.logserver.controller;

import com.zivdah.logserver.dto.ApiResponse;
import com.zivdah.logserver.dto.LogEntryDto;
import com.zivdah.logserver.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LogController {

    private final LogQueryService logQueryService;

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<LogEntryDto>>>> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return logQueryService.search(service, level, from, to, q, PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<LogEntryDto>>builder()
                        .status("success").statusCode(200).message("Logs retrieved successfully").data(list).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<LogEntryDto>>> getById(@PathVariable Long id) {
        return logQueryService.getById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<LogEntryDto>builder()
                        .status("success").statusCode(200).message("Log entry retrieved successfully").data(r).build()));
    }
}
