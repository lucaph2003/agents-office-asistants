package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.exception.AgentOfficeException;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AgentOfficeException.class)
    ResponseEntity<ErrorResponse> handleDomain(AgentOfficeException ex) {
        HttpStatus status = statusOf(ex.code());
        if (status.is5xxServerError()) {
            log.error("event=REQUEST_FAILED code={}", ex.code(), ex);
        } else {
            log.info("event=REQUEST_REJECTED code={} message=\"{}\"", ex.code(), ex.getMessage());
        }
        return respond(status, ex.code().name(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.info("event=REQUEST_REJECTED code={}", ErrorCode.CONCURRENT_MODIFICATION);
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION.name(),
                "The task was modified by another request, retry with fresh data", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                "Invalid value for parameter '%s'".formatted(ex.getName()), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("event=REQUEST_FAILED code={}", ErrorCode.INTERNAL_ERROR, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "Unexpected error", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return withBody(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), "Request validation failed", details);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> parameterName(result) + ": " + error.getDefaultMessage()))
                .sorted()
                .toList();
        return withBody(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), "Request validation failed", details);
    }

    /** Every other Spring MVC exception (malformed JSON, 404, 405, 415...) keeps its status but gets our body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String code = status == HttpStatus.BAD_REQUEST ? ErrorCode.VALIDATION_ERROR.name() : status.name();
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : status.getReasonPhrase();
        return withBody(status, code, message, List.of());
    }

    private ResponseEntity<Object> withBody(HttpStatus status, String code, String message, List<String> details) {
        return ResponseEntity.status(status).body(body(code, message, details));
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message, List<String> details) {
        return ResponseEntity.status(status).body(body(code, message, details));
    }

    private ErrorResponse body(String code, String message, List<String> details) {
        return new ErrorResponse(code, message, MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant(), details);
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter";
    }

    static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_TASK_STATE, CONCURRENT_MODIFICATION, MAX_RETRIES_EXCEEDED -> HttpStatus.CONFLICT;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AGENT_NOT_AVAILABLE, OLLAMA_UNAVAILABLE, TRELLO_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TELEGRAM_NOTIFICATION_FAILED, INVALID_AGENT_OUTPUT -> HttpStatus.BAD_GATEWAY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
