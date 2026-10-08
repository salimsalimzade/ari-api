package app.ari.api.common.exception;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 Problem Details. Our own errors carry a stable {@code code}
 * property; Spring MVC errors (unknown path, wrong method, malformed JSON) use the defaults from
 * {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  public ProblemDetail handleBadRequest(BadRequestException ex) {
    return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
    return problem(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException ex) {
    return problem(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
  }

  /** Spring Security found no valid token; the security entry point hands the error over here. */
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in to continue");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    logger.error("Unhandled exception", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();
    ProblemDetail body = ex.getBody();
    body.setProperty("code", "VALIDATION_FAILED");
    body.setProperty("errors", errors);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setProperty("code", code);
    return body;
  }

  /** One invalid request field in a {@code VALIDATION_FAILED} response. */
  public record FieldViolation(String field, String message) {}
}
