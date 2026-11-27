package io.github.yagmurpazi.testpulse.ingestion.adapter.in.web;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps input errors of the upload API to RFC 9457 problem details. Framework errors (missing parameters, oversized
 * uploads) are rendered as problem details by Spring itself ({@code spring.mvc.problemdetails.enabled}).
 */
@RestControllerAdvice(assignableTypes = TestRunController.class)
class TestRunExceptionHandler {

    @ExceptionHandler(InvalidReportException.class)
    ProblemDetail invalidReport(InvalidReportException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid report");
        return problem;
    }

    /** Domain value objects reject invalid metadata (repository, commit hash, attempt) with this exception. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}
