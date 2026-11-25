package io.github.yagmurpazi.testpulse.analysis.application.port.in;

import io.github.yagmurpazi.testpulse.analysis.domain.InvalidStatusTransitionException;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.util.UUID;

/** Manual status decisions of the team. */
public interface ChangeTestStatusUseCase {

    /**
     * @throws TestNotFoundException            if there is no such test
     * @throws InvalidStatusTransitionException unless the test is flaky
     */
    StatusChange quarantine(UUID testId);

    /**
     * @throws TestNotFoundException            if there is no such test
     * @throws InvalidStatusTransitionException unless the test is quarantined
     */
    StatusChange release(UUID testId);
}
