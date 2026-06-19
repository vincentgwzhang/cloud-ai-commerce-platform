package com.vincent.orderservice.mapper;

import com.vincent.orderservice.dto.OrderResponse;
import com.vincent.orderservice.entity.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);
}
