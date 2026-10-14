package app.ari.api.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Lets the website call the API from the browser with its cookies, and the browser extension with
 * its Bearer token. Spring Security applies it before authentication, so preflight requests never
 * need a token.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

  /**
   * Firefox sends its extensions' requests with {@code Origin: moz-extension://<uuid>}, a uuid of
   * each install, so only a pattern can allow them. Without credentials: an extension proves who it
   * is with its own token, never with the website's cookies.
   */
  private static final List<String> EXTENSION_ORIGINS =
      List.of("moz-extension://*", "chrome-extension://*");

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration website = allowed();
    website.setAllowedOrigins(properties.allowedOrigins());
    website.setAllowCredentials(true);
    CorsConfiguration extension = allowed();
    extension.setAllowedOriginPatterns(EXTENSION_ORIGINS);
    return request -> {
      if (!request.getRequestURI().startsWith("/api/")) {
        return null;
      }
      String origin = request.getHeader(HttpHeaders.ORIGIN);
      return origin != null && extension.checkOrigin(origin) != null ? extension : website;
    };
  }

  private static CorsConfiguration allowed() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    return config;
  }
}
