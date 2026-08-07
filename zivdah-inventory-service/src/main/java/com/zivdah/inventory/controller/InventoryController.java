package com.zivdah.inventory.controller;

import com.zivdah.inventory.dto.AddStockRequestDto;
import com.zivdah.inventory.dto.ApiResponse;
import com.zivdah.inventory.dto.InventoryResponseDto;
import com.zivdah.inventory.dto.ReserveStockRequestDto;
import com.zivdah.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> getInventoryByProductId(
            @PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId)
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Inventory fetched").data(r).build()));
    }

    @PostMapping("/add")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> addStock(
            @RequestBody AddStockRequestDto dto) {
        return inventoryService.addStock(dto.getProductId(), dto.getQuantity())
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock added").data(r).build()));
    }

    @PostMapping("/reserve")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> reserveStock(
            @RequestBody ReserveStockRequestDto dto) {
        return inventoryService.reserveStock(dto.getProductId(), dto.getQuantity())
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock reserved").data(r).build()));
    }

    @PostMapping("/release")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> releaseStock(
            @RequestBody ReserveStockRequestDto dto) {
        return inventoryService.releaseStock(dto.getProductId(), dto.getQuantity())
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock released").data(r).build()));
    }

    @PostMapping("/confirm")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> confirmStock(
            @RequestBody ReserveStockRequestDto dto) {
        return inventoryService.confirmStock(dto.getProductId(), dto.getQuantity())
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock confirmed").data(r).build()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<InventoryResponseDto>>>> getAllInventory(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return inventoryService.getAllInventory(PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<InventoryResponseDto>>builder()
                        .status("success").statusCode(200).message("Inventory retrieved").data(list).build()));
    }
}
