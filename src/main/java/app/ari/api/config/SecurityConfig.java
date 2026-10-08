package app.ari.api.config;

import app.ari.api.feature.account.domain.service.TokenService;
import app.ari.api.feature.account.presentation.google.GoogleSignInFailureHandler;
import app.ari.api.feature.account.presentation.google.GoogleSignInSuccessHandler;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Two filter chains. The Google handshake ({@code /oauth2/**}, {@code /login/oauth2/**}) may use a
 * short session to remember its {@code state}; the API is stateless and reads our JWT from the
 * {@code ari_access} cookie or the {@code Authorization} header.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

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

  /** "Continue with Google". Without Google credentials its URLs answer 404. */
  @Bean
  @Order(1)
  public SecurityFilterChain googleSignInFilterChain(
      HttpSecurity http,
      AuthProperties properties,
      GoogleSignInSuccessHandler successHandler,
      GoogleSignInFailureHandler failureHandler) {
    http.securityMatcher("/oauth2/**", "/login/oauth2/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    AuthProperties.Google google = properties.google();
    if (google.enabled()) {
      http.oauth2Login(
          login ->
              login
                  .clientRegistrationRepository(
                      new InMemoryClientRegistrationRepository(
                          CommonOAuth2Provider.GOOGLE
                              .getBuilder("google")
                              .clientId(google.clientId())
                              .clientSecret(google.clientSecret())
                              .build()))
                  .successHandler(successHandler)
                  .failureHandler(failureHandler));
    }
    return http.build();
  }

  @Bean
  @Order(2)
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

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt
  }

  @Bean
  public JwtEncoder jwtEncoder(AuthProperties properties) {
    return NimbusJwtEncoder.withSecretKey(jwtKey(properties)).build();
  }

  @Bean
  public JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamps, new JwtIssuerValidator(TokenService.ISSUER)));
    return decoder;
  }

  private static SecretKey jwtKey(AuthProperties properties) {
    return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
