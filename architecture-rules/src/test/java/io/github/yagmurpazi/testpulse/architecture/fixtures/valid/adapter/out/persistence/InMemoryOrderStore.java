package io.github.yagmurpazi.testpulse.architecture.fixtures.valid.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.port.out.SaveOrder;
import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.domain.Order;

public class InMemoryOrderStore implements SaveOrder {

    @Override
    public void save(Order order) {
    }
}
