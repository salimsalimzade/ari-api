package app.ari.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sign-in settings ({@code app.auth}).
 *
 * @param jwtSecret HS256 key for access tokens, from {@code APP_JWT_SECRET}
 * @param cookieSecure adds {@code Secure} to the cookies; false only for local http
 * @param cookieDomain shared parent domain in production; blank keeps cookies on the API host
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @NotBlank(message = "set APP_JWT_SECRET") @Size(min = 32, message = "set APP_JWT_SECRET to at least 32 characters") String jwtSecret,
    boolean cookieSecure,
    String cookieDomain) {}
