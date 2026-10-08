package app.ari.api.feature.account.presentation.google;

import app.ari.api.config.AuthProperties;
import app.ari.api.feature.account.application.exception.unauthorized.GoogleSignInRejectedException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.model.request.GoogleProfile;
import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Finishes "Continue with Google": signs the user in with the same cookies as a password sign-in
 * and sends them to the library, or back to the login page when the sign-in is refused.
 */
@Component
public class GoogleSignInSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(GoogleSignInSuccessHandler.class);

  private final AccountFacade facade;
  private final AuthCookies cookies;
  private final AuthProperties properties;

  public GoogleSignInSuccessHandler(
      AccountFacade facade, AuthCookies cookies, AuthProperties properties) {
    this.facade = facade;
    this.cookies = cookies;
    this.properties = properties;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    String target = properties.webUrl() + "/login?error=google";
    if (authentication.getPrincipal() instanceof OidcUser user) {
      try {
        AuthTokens tokens =
            facade.signInWithGoogle(
                new GoogleProfile(
                    user.getSubject(),
                    user.getEmail(),
                    Boolean.TRUE.equals(user.getEmailVerified()),
                    user.getFullName()));
        cookies
            .signIn(tokens)
            .getOrEmpty(HttpHeaders.SET_COOKIE)
            .forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));
        target = properties.webUrl() + "/library";
      } catch (GoogleSignInRejectedException ex) {
        log.info("Google sign-in refused for Google account {}", user.getSubject());
      }
    }
    // The session only carried the OAuth handshake; our cookies take over from here.
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    response.sendRedirect(target);
  }
}
