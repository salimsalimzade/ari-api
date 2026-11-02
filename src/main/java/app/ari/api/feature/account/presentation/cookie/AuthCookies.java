package app.ari.api.feature.account.presentation.cookie;

import app.ari.api.config.AuthProperties;
import app.ari.api.feature.account.application.model.response.AuthTokens;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The two sign-in cookies, and a hint beside them. The sign-in cookies are httpOnly, so page
 * JavaScript never sees a token; all three use {@code Path=/}, so the website's server also
 * receives them while it renders pages.
 */
@Component
public class AuthCookies {

  public static final String ACCESS = "ari_access";
  public static final String REFRESH = "ari_refresh";

  /**
   * Holds no token, so the website's JavaScript may read it: it is there while the access token has
   * at least a minute left. Before a request that is costly to send twice (an upload), the website
   * refreshes when it is gone, instead of sending it to get a 401.
   */
  public static final String ACCESS_FRESH = "ari_access_fresh";

  /**
   * The PKCE verifier of a Google sign-in, between leaving for Google and coming back. Only the
   * Google endpoints get it.
   */
  public static final String GOOGLE_VERIFIER = "ari_google";

  /**
   * The access cookie goes this much before its JWT expires, so a cookie still sent never holds an
   * expired one; the freshness cookie goes twice as early, which leaves time to refresh.
   */
  static final Duration ACCESS_MARGIN = Duration.ofMinutes(1);

  /** Supabase keeps a session while it is refreshed; the cookie gives it 30 days without. */
  static final Duration REFRESH_MAX_AGE = Duration.ofDays(30);

  static final Duration GOOGLE_VERIFIER_MAX_AGE = Duration.ofMinutes(10);

  static final String GOOGLE_PATH = "/api/v1/auth/google";

  private final AuthProperties properties;

  public AuthCookies(AuthProperties properties) {
    this.properties = properties;
  }

  /** {@code Set-Cookie} headers that sign the browser in. */
  public HttpHeaders signIn(AuthTokens tokens) {
    Duration access = tokens.expiresIn().minus(ACCESS_MARGIN);
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, tokens.accessToken(), access, true));
    headers.add(
        HttpHeaders.SET_COOKIE, cookie(REFRESH, tokens.refreshToken(), REFRESH_MAX_AGE, true));
    headers.add(
        HttpHeaders.SET_COOKIE, cookie(ACCESS_FRESH, "1", access.minus(ACCESS_MARGIN), false));
    return headers;
  }

  /** {@code Set-Cookie} for the verifier, which only the Google endpoints receive. */
  public String googleVerifier(String verifier) {
    return cookie(GOOGLE_VERIFIER, verifier, GOOGLE_VERIFIER_MAX_AGE, true, GOOGLE_PATH);
  }

  /** {@code Set-Cookie} that deletes it: it works once. */
  public String clearGoogleVerifier() {
    return cookie(GOOGLE_VERIFIER, "", Duration.ZERO, true, GOOGLE_PATH);
  }

  /** {@code Set-Cookie} headers that delete all three cookies. */
  public HttpHeaders clear() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", Duration.ZERO, true));
    headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", Duration.ZERO, true));
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_FRESH, "", Duration.ZERO, false));
    return headers;
  }

  private String cookie(String name, String value, Duration maxAge, boolean httpOnly) {
    return cookie(name, value, maxAge, httpOnly, "/");
  }

  // Lax, so the verifier comes along when Google's answer opens the callback (a top-level GET).
  private String cookie(String name, String value, Duration maxAge, boolean httpOnly, String path) {
    ResponseCookie.ResponseCookieBuilder cookie =
        ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(properties.cookieSecure())
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge);
    if (StringUtils.hasText(properties.cookieDomain())) {
      cookie.domain(properties.cookieDomain());
    }
    return cookie.build().toString();
  }
}
