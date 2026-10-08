package app.ari.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Sign-in settings ({@code app.auth}).
 *
 * @param jwtSecret HS256 key for access tokens, from {@code APP_JWT_SECRET}
 * @param cookieSecure adds {@code Secure} to the cookies; false only for local http
 * @param cookieDomain shared parent domain in production; blank keeps cookies on the API host
 * @param webUrl the website, where Google sign-in sends the user back
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @NotBlank(message = "set APP_JWT_SECRET") @Size(min = 32, message = "set APP_JWT_SECRET to at least 32 characters") String jwtSecret,
    boolean cookieSecure,
    String cookieDomain,
    @NotBlank String webUrl,
    @Valid @NotNull Google google) {

  /** Google OAuth client from {@code GOOGLE_CLIENT_ID} and {@code GOOGLE_CLIENT_SECRET}. */
  public record Google(String clientId, String clientSecret) {

    /** Google sign-in is on only when both values are set. */
    public boolean enabled() {
      return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }
  }
}
