package com.zivdah.inventory.controller;

import com.zivdah.inventory.client.ProductServiceClient;
import com.zivdah.inventory.dto.AddStockRequestDto;
import com.zivdah.inventory.dto.ApiResponse;
import com.zivdah.inventory.dto.InventoryResponseDto;
import com.zivdah.inventory.dto.ReserveStockRequestDto;
import com.zivdah.inventory.dto.SyncQuantityRequestDto;
import com.zivdah.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductServiceClient productServiceClient;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    private Mono<String> currentRole() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> auth.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.replaceFirst("^ROLE_", ""))
                        .orElse(""));
    }

    // Pushes the new availableQuantity to product-service's stockQuantity after THIS service
    // is the one that changed it (add/reserve/release). Fire-and-forget from the caller's
    // point of view — syncStockQuantity swallows its own errors so a product-service hiccup
    // never fails the inventory mutation that's already succeeded.
    private Mono<InventoryResponseDto> pushToProduct(InventoryResponseDto r) {
        return productServiceClient.syncStockQuantity(r.getProductId(), r.getAvailableQuantity()).thenReturn(r);
    }

    @GetMapping("/{productId}")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> getInventoryByProductId(
            @PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId)
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Inventory fetched").data(r).build()));
    }

    // Internal, no auth — see SecurityConfig. Called by product-service after ITS
    // stockQuantity changes, to push the new value here directly (a "set", not an "add").
    // Must never push back to product-service, or the two services would loop forever.
    @PutMapping("/{productId}/sync-quantity")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> syncQuantity(
            @PathVariable Long productId, @RequestBody SyncQuantityRequestDto dto) {
        return inventoryService.setAvailableQuantitySync(productId, dto.getAvailableQuantity())
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Quantity synced").data(r).build()));
    }

    // A VENDOR may only add stock for a product they own; ADMIN may add stock for any
    // product. Ownership isn't known to this service (it only stores productId), so it's
    // checked via a call to product-service — see ProductServiceClient.
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> addStock(
            @RequestBody AddStockRequestDto dto) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> {
                    if (!"VENDOR".equalsIgnoreCase(t.getT2())) {
                        return Mono.empty(); // ADMIN — no ownership check
                    }
                    Long callerId = t.getT1();
                    return productServiceClient.getProductVendorId(dto.getProductId())
                            .filter(callerId::equals)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Not the owner of this product")));
                })
                .then(inventoryService.addStock(dto.getProductId(), dto.getQuantity()))
                .flatMap(this::pushToProduct)
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock added").data(r).build()));
    }

    // Not driven by any UI today — reserve/release/confirm are invoked internally by
    // InventoryEventConsumer (a direct service-layer call that bypasses this controller and
    // its security filter chain entirely), so restricting these HTTP endpoints to ADMIN has
    // no effect on that flow. Kept ADMIN-reachable only for manual ops/support use.
    @PostMapping("/reserve")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> reserveStock(
            @RequestBody ReserveStockRequestDto dto) {
        return inventoryService.reserveStock(dto.getProductId(), dto.getQuantity())
                .flatMap(this::pushToProduct)
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock reserved").data(r).build()));
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<InventoryResponseDto>>> releaseStock(
            @RequestBody ReserveStockRequestDto dto) {
        return inventoryService.releaseStock(dto.getProductId(), dto.getQuantity())
                .flatMap(this::pushToProduct)
                .map(r -> ResponseEntity.ok(ApiResponse.<InventoryResponseDto>builder()
                        .status("success").statusCode(200).message("Stock released").data(r).build()));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('ADMIN')")
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
