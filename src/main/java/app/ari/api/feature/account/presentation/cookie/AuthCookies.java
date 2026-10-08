package app.ari.api.feature.account.presentation.cookie;

import app.ari.api.config.AuthProperties;
import app.ari.api.feature.account.application.model.response.AuthTokens;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The two sign-in cookies. Both are httpOnly, so page JavaScript never sees a token, and both use
 * {@code Path=/}, so the website's server also receives them while it renders pages.
 */
@Component
public class AuthCookies {

  public static final String ACCESS = "ari_access";
  public static final String REFRESH = "ari_refresh";

  /**
   * One minute shorter than the 15-minute JWT, so a cookie still sent never holds an expired one.
   */
  static final Duration ACCESS_MAX_AGE = Duration.ofMinutes(14);

  static final Duration REFRESH_MAX_AGE = Duration.ofDays(30);

  private final AuthProperties properties;

  public AuthCookies(AuthProperties properties) {
    this.properties = properties;
  }

  /** {@code Set-Cookie} headers that sign the browser in. */
  public HttpHeaders signIn(AuthTokens tokens) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, tokens.accessToken(), ACCESS_MAX_AGE));
    headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, tokens.refreshToken(), REFRESH_MAX_AGE));
    return headers;
  }

  /** {@code Set-Cookie} headers that delete both cookies. */
  public HttpHeaders clear() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", Duration.ZERO));
    headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", Duration.ZERO));
    return headers;
  }

  private String cookie(String name, String value, Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge);
    if (StringUtils.hasText(properties.cookieDomain())) {
      cookie.domain(properties.cookieDomain());
    }
    return cookie.build().toString();
  }
}
