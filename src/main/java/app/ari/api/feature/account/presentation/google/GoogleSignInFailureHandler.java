package app.ari.api.feature.account.presentation.google;

import app.ari.api.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * The Google handshake failed: the user cancelled, the {@code state} didn't match, or Google
 * returned an error. Sends the user back to the login page, which shows a message.
 */
@Component
public class GoogleSignInFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(GoogleSignInFailureHandler.class);

  private final AuthProperties properties;

  public GoogleSignInFailureHandler(AuthProperties properties) {
    this.properties = properties;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.info("Google sign-in failed: {}", exception.getMessage());
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    response.sendRedirect(properties.webUrl() + "/login?error=google");
  }
}
