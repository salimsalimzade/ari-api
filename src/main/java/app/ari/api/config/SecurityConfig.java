package app.ari.api.config;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The API is stateless and reads a Supabase access token from the {@code ari_access} cookie or the
 * {@code Authorization} header. Tokens are checked against the project's public signing keys
 * (JWKS), so ari-api never holds a key that could sign one.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

  /** The {@code aud} of a signed-in user's token; the project's API keys have none. */
  public static final String AUDIENCE = "authenticated";

  private static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/**",
    "/actuator/health",
    "/actuator/health/**",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/error"
  };

  @Bean
  public SecurityFilterChain apiFilterChain(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    AuthenticationEntryPoint entryPoint =
        new ProblemDetailsAuthenticationEntryPoint(exceptionResolver);
    http.csrf(AbstractHttpConfigurer::disable) // SameSite=Lax cookies + CORS guard the API
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            requests ->
                requests.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .bearerTokenResolver(new CookieBearerTokenResolver())
                    .authenticationEntryPoint(entryPoint)
                    .jwt(Customizer.withDefaults()));
    return http.build();
  }

  /** Supabase's asymmetric signing keys: ES256 (the default) or RS256, fetched and cached. */
  @Bean
  public JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.authUrl() + "/.well-known/jwks.json")
            .jwsAlgorithms(
                algorithms -> {
                  algorithms.add(SignatureAlgorithm.ES256);
                  algorithms.add(SignatureAlgorithm.RS256);
                })
            .build();
    decoder.setJwtValidator(tokenValidator(properties.publicAuthUrl(), clock));
    return decoder;
  }

  /** Not expired, from our project's Auth, and a signed-in user's: what every token must be. */
  public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, Clock clock) {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    return new DelegatingOAuth2TokenValidator<>(
        timestamps,
        new JwtIssuerValidator(issuer),
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, audience -> audience != null && audience.contains(AUDIENCE)));
  }
}
