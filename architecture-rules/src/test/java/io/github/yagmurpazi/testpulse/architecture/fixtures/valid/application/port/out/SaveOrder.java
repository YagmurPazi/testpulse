package io.github.yagmurpazi.testpulse.architecture.fixtures.valid.application.port.out;

import io.github.yagmurpazi.testpulse.architecture.fixtures.valid.domain.Order;

public interface SaveOrder {
    void save(Order order);
}
