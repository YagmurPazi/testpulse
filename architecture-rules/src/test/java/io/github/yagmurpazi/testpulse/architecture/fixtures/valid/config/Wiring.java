package io.github.yagmurpazi.testpulse.architecture.fixtures.valid.config;

import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.adapter.in.web.OrderController;
import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.adapter.out.persistence.InMemoryOrderStore;
import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.service.PlaceOrderService;

public class Wiring {

    public OrderController orderController() {
        return new OrderController(new PlaceOrderService(new InMemoryOrderStore()));
    }
}
