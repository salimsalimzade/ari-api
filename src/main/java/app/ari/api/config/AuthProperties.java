package app.ari.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Sign-in settings ({@code app.auth}). Supabase Auth keeps the accounts and issues the tokens;
 * ari-api calls it with the project's secret key and puts the tokens into the website's cookies.
 *
 * @param supabaseUrl where ari-api reaches the Supabase project, from {@code SUPABASE_URL}
 * @param supabasePublicUrl where browsers reach it, for Google sign-in, and what its tokens name as
 *     their issuer; differs from {@code supabaseUrl} when ari-api runs in Docker ({@code
 *     SUPABASE_PUBLIC_URL}, blank for the same)
 * @param supabaseSecretKey the project's secret key ({@code sb_secret_...}) for the admin API, from
 *     {@code SUPABASE_SECRET_KEY}; it never leaves ari-api
 * @param apiUrl where browsers reach ari-api, for Google's return address ({@code APP_API_URL})
 * @param cookieSecure adds {@code Secure} to the cookies; false only for local http
 * @param cookieDomain shared parent domain in production; blank keeps cookies on the API host
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @NotBlank(message = "set SUPABASE_URL") String supabaseUrl,
    String supabasePublicUrl,
    @NotBlank(message = "set SUPABASE_SECRET_KEY") String supabaseSecretKey,
    @NotBlank(message = "set APP_API_URL") String apiUrl,
    boolean cookieSecure,
    String cookieDomain) {

  /** Supabase Auth's base URL for ari-api's own calls. */
  public String authUrl() {
    return strip(supabaseUrl) + "/auth/v1";
  }

  /** Supabase Auth's base URL as browsers see it, which is also its tokens' {@code iss}. */
  public String publicAuthUrl() {
    return strip(StringUtils.hasText(supabasePublicUrl) ? supabasePublicUrl : supabaseUrl)
        + "/auth/v1";
  }

  private static String strip(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
