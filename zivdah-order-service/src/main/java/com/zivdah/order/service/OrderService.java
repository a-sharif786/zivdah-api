package com.zivdah.order.service;

import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface OrderService {
    OrderResponseDto createOrder(OrderRequestDto dto);

    OrderResponseDto getOrderById(Long orderId);

    List<OrderResponseDto> getOrdersByUser(Long userId);

    void cancelOrder(Long orderId);
}
