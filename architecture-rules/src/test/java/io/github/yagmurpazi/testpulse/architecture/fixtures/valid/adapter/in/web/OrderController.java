package io.github.yagmurpazi.testpulse.architecture.fixtures.valid.adapter.in.web;

import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.port.in.PlaceOrder;

public class OrderController {

    private final PlaceOrder placeOrder;

    public OrderController(PlaceOrder placeOrder) {
        this.placeOrder = placeOrder;
    }

    public void post(String id) {
        placeOrder.place(id);
    }
}
