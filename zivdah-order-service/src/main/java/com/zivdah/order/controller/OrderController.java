package com.zivdah.order.controller;


import com.zivdah.order.dto.ApiResponse;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @RequestBody OrderRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Order created successfully")
                .data(orderService.createOrder(dto))
                .build());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Order retrieved successfully")
                .data(orderService.getOrderById(orderId))
                .build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponseDto>>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Orders retrieved successfully")
                .data(orderService.getOrdersByUser(userId))
                .build());
    }

    @PutMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Order cancelled successfully")
                .data(null)
                .build());
    }
}