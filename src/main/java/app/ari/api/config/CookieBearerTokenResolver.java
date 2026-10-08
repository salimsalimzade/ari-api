package app.ari.api.config;

import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * Finds the access token: the {@code ari_access} cookie (website), then the {@code Authorization:
 * Bearer} header (browser extension). The sign-in endpoints get no token at all, so an old cookie
 * can never stop someone from signing in, refreshing or signing out.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

  private static final RequestMatcher AUTH_ENDPOINTS =
      PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");

  private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    if (AUTH_ENDPOINTS.matches(request)) {
      return null;
    }
    Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS);
    if (cookie != null && StringUtils.hasText(cookie.getValue())) {
      return cookie.getValue();
    }
    return headerResolver.resolve(request);
  }
}
