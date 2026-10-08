package app.ari.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers a missing or invalid token with a 401 Problem Detail. Hands the error to Spring MVC, so
 * {@code GlobalExceptionHandler} writes it like every other error.
 */
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final HandlerExceptionResolver exceptionResolver;

  public ProblemDetailsAuthenticationEntryPoint(HandlerExceptionResolver exceptionResolver) {
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) {
    exceptionResolver.resolveException(request, response, null, authException);
  }
}
