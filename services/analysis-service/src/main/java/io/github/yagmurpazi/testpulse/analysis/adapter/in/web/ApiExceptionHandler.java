package io.github.yagmurpazi.testpulse.analysis.adapter.in.web;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.TestNotFoundException;
import io.github.yagmurpazi.testpulse.analysis.domain.InvalidStatusTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps application errors to RFC 9457 problem details. */
@RestControllerAdvice(assignableTypes = {TestQueryController.class, TestStatusController.class})
class ApiExceptionHandler {

    @ExceptionHandler(TestNotFoundException.class)
    ProblemDetail notFound(TestNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Test not found", e.getMessage());
    }

    /** The request is valid, but the test's current status does not allow the action. */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail conflict(InvalidStatusTransitionException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Status change not allowed", e.getMessage());
        problem.setProperty("currentStatus", e.currentStatus());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
