package com.payflow.platform.web;

import com.payflow.shared.application.ConflictException;
import com.payflow.shared.application.DependencyUnavailableException;
import com.payflow.shared.application.ForbiddenException;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.UnprocessableException;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.InvalidStateTransitionException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Translates exceptions into RFC 9457 {@code application/problem+json} responses.
 *
 * <p>The mapping is by exception <em>category</em>, not by use case, so adding a use case never requires
 * touching this class. Internal details (stack traces, SQL, class names) are never returned; 5xx responses
 * carry only the correlation id, and the detail goes to the logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException e) {
        return respond(HttpStatus.FORBIDDEN, e.code(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return respond(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ProblemDetail> invalidTransition(InvalidStateTransitionException e) {
        return respond(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(UnprocessableException.class)
    ResponseEntity<ProblemDetail> unprocessable(UnprocessableException e) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    ResponseEntity<ProblemDetail> ruleViolation(DomainRuleViolationException e) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(DependencyUnavailableException e) {
        log.atWarn().setCause(e).addKeyValue("code", e.code()).log("dependency unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(Problems.of(HttpStatus.SERVICE_UNAVAILABLE, e.code(), e.getMessage()));
    }

    /** Bean Validation raised outside Spring MVC's own parameter validation (defensive: still a client error). */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> Map.of("field", String.valueOf(v.getPropertyPath()), "message", v.getMessage()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlationId when contacting support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldError)
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(ApiExceptionHandler::parameterErrors)
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Framework-raised problems (400 malformed JSON, 405, 415, ...) also get the code and correlation id. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", "HTTP_" + statusCode.value());
            }
            Problems.enrich(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** Body (bean) parameters report their individual fields; simple parameters report the parameter name. */
    private static Stream<Map<String, String>> parameterErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors beanErrors) {
            return beanErrors.getFieldErrors().stream().map(ApiExceptionHandler::fieldError);
        }
        String parameter = result.getMethodParameter().getParameterName();
        return result.getResolvableErrors().stream().map(err -> Map.of(
                "field", parameter == null ? "?" : parameter, "message", String.valueOf(err.getDefaultMessage())));
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(Problems.of(status, code, detail));
    }
}
