package io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.service;

import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.port.in.PlaceOrder;
import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.port.out.SaveOrder;
import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.domain.Order;

public class PlaceOrderService implements PlaceOrder {

    private final SaveOrder saveOrder;

    public PlaceOrderService(SaveOrder saveOrder) {
        this.saveOrder = saveOrder;
    }

    @Override
    public void place(String id) {
        saveOrder.save(new Order(id));
    }
}
