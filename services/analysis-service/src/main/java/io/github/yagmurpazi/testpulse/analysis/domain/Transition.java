package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Objects;

/**
 * A change to another state, with the reason a person would read.
 *
 * @param target the state after the change
 * @param reason why the state changes
 */
public record Transition(TestState target, String reason) {

    public Transition {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("a transition needs a reason");
        }
    }
}
