package com.tickethub.booking.service;

import com.tickethub.booking.api.dto.OrderView;
import com.tickethub.booking.domain.Order;
import com.tickethub.booking.domain.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper
public interface OrderMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "secondsUntilExpiry", expression = "java(order.remaining(now).toSeconds())")
    OrderView toView(Order order, Instant now);

    OrderView.Item toView(OrderItem item);
}
