package com.zivdah.order.controller;

import com.zivdah.order.dto.ApiResponse;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> createOrder(
            @RequestBody OrderRequestDto dto) {
        return orderService.createOrder(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order created successfully").data(r).build()));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> getOrder(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId)
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order retrieved successfully").data(r).build()));
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<OrderResponseDto>>>> getOrdersByUser(@PathVariable Long userId) {
        return orderService.getOrdersByUser(userId)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                        .status("success").statusCode(200).message("Orders retrieved successfully").data(list).build()));
    }

    @PutMapping("/cancel/{orderId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> cancelOrder(@PathVariable Long orderId) {
        return orderService.cancelOrder(orderId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order cancelled successfully").build()));
    }
}
