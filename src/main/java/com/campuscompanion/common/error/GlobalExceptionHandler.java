package com.campuscompanion.common.error;

import com.campuscompanion.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place where exceptions become HTTP responses. Every error leaves the API in the same
 * shape (RFC 9457 problem details with a stable {@code code} and a {@code traceId}), and nothing
 * internal, such as a stack trace, SQL or a class name, reaches the client. The format is defined
 * in docs/09-api-contract.md section 3.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Failures the application raised on purpose. Expected, so logged without a stack trace. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
    ErrorCode code = ex.getCode();
    log.info("Request failed with {}: {}", code, ex.getMessage());
    ProblemDetail problem = problem(code.status(), code.title(), ex.getMessage(), code, request);
    return ResponseEntity.status(code.status()).body(problem);
  }

  /** Invalid request parameters, for example a page size above the maximum. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, WebRequest request) {
    List<FieldErrorDetail> errors =
        ex.getConstraintViolations().stream()
            .map(GlobalExceptionHandler::toFieldError)
            .sorted(Comparator.comparing(FieldErrorDetail::field))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors, request));
  }

  /** Anything unexpected. The details go to the log; the client gets only a reference. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unexpected failure", ex);
    ErrorCode code = ErrorCode.INTERNAL_ERROR;
    ProblemDetail problem =
        problem(
            code.status(),
            code.title(),
            "Something went wrong on our side. Please try again.",
            code,
            request);
    return ResponseEntity.status(code.status()).body(problem);
  }

  /** An invalid request body: one entry per invalid field. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorDetail> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(FieldErrorDetail::field))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors, request));
  }

  /**
   * Every other failure raised by Spring MVC itself (unreadable JSON, a wrong parameter type, an
   * unknown path, an unsupported method). Spring's own message can name classes and parameters, so
   * it is replaced with a plain one.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    HttpStatus status = HttpStatus.valueOf(statusCode.value());
    if (status.is5xxServerError()) {
      log.error("Request failed with status {}", status.value(), ex);
    } else {
      log.info(
          "Request rejected with status {}: {}", status.value(), ex.getClass().getSimpleName());
    }
    ErrorCode code = codeFor(status);
    String title = code != null ? code.title() : status.getReasonPhrase();
    ProblemDetail problem = problem(status, title, plainDetailFor(status), code, request);
    return new ResponseEntity<>(problem, headers, status);
  }

  private ProblemDetail validationProblem(List<FieldErrorDetail> errors, WebRequest request) {
    ErrorCode code = ErrorCode.VALIDATION_FAILED;
    ProblemDetail problem =
        problem(code.status(), code.title(), "One or more fields are invalid.", code, request);
    problem.setProperty("errors", errors);
    return problem;
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, ErrorCode code, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    if (request instanceof ServletWebRequest servletRequest) {
      problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
    }
    if (code != null) {
      problem.setProperty("code", code.name());
    }
    problem.setProperty("traceId", MDC.get(RequestIdFilter.MDC_KEY));
    return problem;
  }

  private static FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
    return new FieldErrorDetail(field, violation.getMessage());
  }

  /** The documented code for a status, where the API contract defines exactly one. */
  private static ErrorCode codeFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> ErrorCode.VALIDATION_FAILED;
      case NOT_FOUND -> ErrorCode.NOT_FOUND;
      case INTERNAL_SERVER_ERROR -> ErrorCode.INTERNAL_ERROR;
      default -> null;
    };
  }

  private static String plainDetailFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "The request could not be understood. Check the values and try again.";
      case NOT_FOUND -> "We could not find that.";
      case METHOD_NOT_ALLOWED -> "That action is not available here.";
      case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> "That format is not supported.";
      default ->
          status.is5xxServerError()
              ? "Something went wrong on our side. Please try again."
              : "The request could not be completed.";
    };
  }
}
