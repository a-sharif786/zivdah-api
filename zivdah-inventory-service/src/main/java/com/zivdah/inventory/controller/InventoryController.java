package com.zivdah.inventory.controller;

import com.zivdah.inventory.dto.AddStockRequestDto;
import com.zivdah.inventory.dto.ApiResponse;
import com.zivdah.inventory.dto.InventoryResponseDto;
import com.zivdah.inventory.dto.ReserveStockRequestDto;
import com.zivdah.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restful/v1/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // 🔍 Get inventory
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> getInventoryByProductId(@PathVariable Long productId) {
        InventoryResponseDto inventory = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Inventory fetched successfully")
                .data(inventory)
                .build()
        );
    }

    // ➕ Add stock (Admin)
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> addStock(
            @RequestBody AddStockRequestDto dto) {
        InventoryResponseDto inventory = inventoryService.addStock(dto.getProductId(), dto.getQuantity());

        // Wrap the response in ApiResponse
        return ResponseEntity.ok(
                ApiResponse.<InventoryResponseDto>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Stock added successfully")
                        .data(inventory)
                        .build()
        );
    }

    // 🔒 Reserve stock (Order Created)
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> reserveStock(
            @RequestBody ReserveStockRequestDto dto) {
        InventoryResponseDto inventory = inventoryService.reserveStock(dto.getProductId(), dto.getQuantity());
        // Wrap the response in ApiResponse
        return ResponseEntity.ok(
                ApiResponse.<InventoryResponseDto>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Reserve Stock added successfully")
                        .data(inventory)
                        .build()
        );
    }

    // 🔓 Release stock (Payment Failed)
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> releaseStock(
            @RequestBody ReserveStockRequestDto dto) {


        InventoryResponseDto inventory = inventoryService.releaseStock(dto.getProductId(), dto.getQuantity());
        // Wrap the response in ApiResponse
        return ResponseEntity.ok(
                ApiResponse.<InventoryResponseDto>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Release Stock added successfully")
                        .data(inventory)
                        .build()
        );
    }

    // ✅ Confirm stock (Payment Success)
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> confirmStock(
            @RequestBody ReserveStockRequestDto dto) {

        InventoryResponseDto inventory = inventoryService.confirmStock(dto.getProductId(), dto.getQuantity());
        // Wrap the response in ApiResponse
        return ResponseEntity.ok(
                ApiResponse.<InventoryResponseDto>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Confirm Stock added successfully")
                        .data(inventory)
                        .build()
        );
    }
}
