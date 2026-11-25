package io.github.yagmurpazi.testpulse.analysis.adapter.in.web;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual status decisions: actions on a test, so POST to a sub-resource rather than a status field update. */
@RestController
@RequestMapping("/api/v1/tests/{testId}")
class TestStatusController {

    private final ChangeTestStatusUseCase changeTestStatus;

    TestStatusController(ChangeTestStatusUseCase changeTestStatus) {
        this.changeTestStatus = changeTestStatus;
    }

    @PostMapping("/quarantine")
    StatusChangeResponse quarantine(@PathVariable UUID testId) {
        return StatusChangeResponse.from(changeTestStatus.quarantine(testId));
    }

    @PostMapping("/release")
    StatusChangeResponse release(@PathVariable UUID testId) {
        return StatusChangeResponse.from(changeTestStatus.release(testId));
    }

    record StatusChangeResponse(UUID testId, TestStatus from, TestStatus to, String reason, Instant changedAt) {

        static StatusChangeResponse from(StatusChange change) {
            return new StatusChangeResponse(change.testId(), change.from(), change.to(), change.reason(),
                    change.changedAt());
        }
    }
}
