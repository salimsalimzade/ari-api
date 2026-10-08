package app.ari.api.feature.account.presentation;

import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
    name = "Account",
    description =
        "Sign-in and the signed-in user. Signing in sets two httpOnly cookies: ari_access (JWT, 15"
            + " minutes) and ari_refresh (30 days, replaced on every refresh).")
@RequestMapping("/api/v1")
public interface AccountController {

  @Operation(summary = "Register with email and password, and sign in")
  @SecurityRequirements
  @ApiResponse(responseCode = "201", description = "Registered; both cookies are set")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "409", description = "EMAIL_TAKEN")
  @PostMapping("/auth/register")
  ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest request);

  @Operation(summary = "Sign in with email and password")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "Signed in; both cookies are set")
  @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS")
  @PostMapping("/auth/login")
  ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request);

  @Operation(summary = "Replace both cookies using the ari_refresh cookie")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "New cookies are set")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: sign in again; both cookies are cleared")
  @PostMapping("/auth/refresh")
  ResponseEntity<Void> refresh(
      @Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH, required = false)
          String refreshToken);

  @Operation(summary = "Sign out: end the session and clear both cookies")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "Signed out")
  @PostMapping("/auth/logout")
  ResponseEntity<Void> logout(
      @Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH, required = false)
          String refreshToken);

  @Operation(summary = "The signed-in user")
  @ApiResponse(responseCode = "200", description = "The signed-in user")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping("/me")
  MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);
}
