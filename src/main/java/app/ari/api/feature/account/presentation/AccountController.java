package app.ari.api.feature.account.presentation;

import app.ari.api.feature.account.application.model.request.EmailRequest;
import app.ari.api.feature.account.application.model.request.ExtensionCodeRequest;
import app.ari.api.feature.account.application.model.request.ExtensionRefreshRequest;
import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.PasswordResetCheckRequest;
import app.ari.api.feature.account.application.model.request.PasswordResetConfirmRequest;
import app.ari.api.feature.account.application.model.request.PasswordResetRequest;
import app.ari.api.feature.account.application.model.request.ProfileRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.request.VerifyEmailRequest;
import app.ari.api.feature.account.application.model.response.ExtensionCodeResponse;
import app.ari.api.feature.account.application.model.response.ExtensionTokensResponse;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.PasswordResetCheckResponse;
import app.ari.api.feature.account.application.model.response.UsernameAvailabilityResponse;
import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Account",
    description =
        "Sign-in and the signed-in user. Signing in sets two httpOnly cookies: ari_access (JWT, 15"
            + " minutes) and ari_refresh (30 days, replaced on every refresh). The browser"
            + " extension has a session of its own: its tokens travel as JSON and Authorization:"
            + " Bearer, never as cookies.")
@RequestMapping("/api/v1")
public interface AccountController {

  /** The website's language cookie: {@code en} or {@code az}. */
  String LANGUAGE_COOKIE = "ari_lang";

  @Operation(summary = "Register with username and password, and sign in")
  @SecurityRequirements
  @ApiResponse(responseCode = "201", description = "Registered; both cookies are set")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "409", description = "USERNAME_TAKEN")
  @PostMapping("/auth/register")
  ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest request);

  @Operation(
      summary = "Is this username free?",
      description =
          "Public, for the live check while someone types a username. A Bloom filter in Redis"
              + " answers most free names without the database; the database decides the rest."
              + " At most 30 checks a minute from one address.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "available is false when an account has it")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: not a valid username")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
  @GetMapping("/auth/username-available")
  UsernameAvailabilityResponse checkUsername(
      @Parameter(
              description = "3 to 30 letters, digits, underscores or dots, in any case",
              example = "ada.lovelace")
          @RequestParam
          @Pattern(
              regexp = "[A-Za-z0-9_.]{3,30}",
              message = "must be 3 to 30 letters, digits, underscores or dots")
          String username,
      @Parameter(hidden = true) HttpServletRequest request);

  @Operation(summary = "Sign in with username and password")
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

  @Operation(
      summary = "A code that signs the browser extension in",
      description =
          "For the signed-in website, which hands the code to the extension in the same browser."
              + " It works once, within 60 seconds.")
  @ApiResponse(responseCode = "200", description = "The code")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @PostMapping("/me/extension-code")
  ExtensionCodeResponse extensionCode(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

  @Operation(
      summary = "A code that signs the browser extension in as another account",
      description =
          "For the website's extension sign-in page: the extension can use another account than"
              + " the website, whose cookies stay as they are. The code works once, within 60"
              + " seconds.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "The code; no cookies are set")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS")
  @PostMapping("/auth/extension/code")
  ExtensionCodeResponse extensionCodeForAccount(@Valid @RequestBody LoginRequest request);

  @Operation(
      summary = "Swap the website's code for the extension's tokens",
      description =
          "Starts the extension's own session, as the user who asked for the code. It lasts while"
              + " the extension refreshes at least every 30 days, or until it signs out.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "The extension's tokens")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "INVALID_CODE: unknown, expired or used code")
  @PostMapping("/auth/extension/token")
  ExtensionTokensResponse connectExtension(@Valid @RequestBody ExtensionCodeRequest request);

  @Operation(summary = "Replace the extension's tokens using its refresh token")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "A new pair; the old refresh token is used up")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: sign the extension in again")
  @PostMapping("/auth/extension/refresh")
  ExtensionTokensResponse refreshExtension(@Valid @RequestBody ExtensionRefreshRequest request);

  @Operation(summary = "Sign the extension out: end its session")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "Signed out")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @PostMapping("/auth/extension/logout")
  ResponseEntity<Void> logoutExtension(@Valid @RequestBody ExtensionRefreshRequest request);

  @Operation(summary = "The signed-in user")
  @ApiResponse(responseCode = "200", description = "The signed-in user")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping("/me")
  MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

  @Operation(
      summary = "Add or change the email",
      description =
          "Makes it the pending email and emails it a verify link, in the language of the ari_lang"
              + " cookie. A verified email keeps working until the new one is confirmed. Whether"
              + " another account uses the address is only checked when the link is opened.")
  @ApiResponse(responseCode = "202", description = "The link is on its way; pendingEmail is set")
  @ApiResponse(
      responseCode = "200",
      description = "It already is the verified email; nothing is sent")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: over 1 link a minute or 5 a day")
  @PutMapping("/me/email")
  ResponseEntity<MeResponse> changeEmail(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EmailRequest request,
      @Parameter(hidden = true) @CookieValue(name = LANGUAGE_COOKIE, required = false)
          String language);

  @Operation(summary = "Send the pending email a new verify link")
  @ApiResponse(responseCode = "202", description = "The link is on its way")
  @ApiResponse(responseCode = "400", description = "BAD_REQUEST: no email is waiting for its link")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "429", description = "RATE_LIMITED: over 1 link a minute or 5 a day")
  @PostMapping("/me/email/resend")
  ResponseEntity<MeResponse> resendVerification(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(hidden = true) @CookieValue(name = LANGUAGE_COOKIE, required = false)
          String language);

  @Operation(summary = "Remove the verified and the pending email")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @DeleteMapping("/me/email")
  ResponseEntity<Void> removeEmail(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

  @Operation(
      summary = "Confirm an email with the code from its link",
      description = "Public: the link may be opened while signed out or in another browser.")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "The pending email is now the verified email")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(
      responseCode = "401",
      description = "INVALID_CODE: unknown, expired or used link, or a newer address is waiting")
  @ApiResponse(responseCode = "409", description = "EMAIL_TAKEN: another account verified it first")
  @PostMapping("/auth/verify-email")
  ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request);

  @Operation(
      summary = "Email a link to reset a forgotten password",
      description =
          "Public. Takes a username, or an account's verified email, and emails a link to that"
              + " account's verified email, in the language of the ari_lang cookie. The link works"
              + " once, for 30 minutes. The answer says why no link was sent, so it tells whether"
              + " an account exists; at most 20 requests an hour come from one address.")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "The link is on its way")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(
      responseCode = "404",
      description = "ACCOUNT_NOT_FOUND: no account has that username or verified email")
  @ApiResponse(
      responseCode = "409",
      description =
          "NO_EMAIL: the account has no email; EMAIL_NOT_CONFIRMED: its email (or the one given)"
              + " still waits for its verify link")
  @ApiResponse(
      responseCode = "429",
      description =
          "RATE_LIMITED: over 20 requests an hour from this address, or the account's 1 email a"
              + " minute, 5 a day")
  @PostMapping("/auth/password-reset")
  ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request,
      @Parameter(hidden = true) @CookieValue(name = LANGUAGE_COOKIE, required = false)
          String language,
      @Parameter(hidden = true) HttpServletRequest httpRequest);

  @Operation(
      summary = "Whose password a reset link changes",
      description =
          "Public. Checking never uses the link up, so the reset page can check while it renders.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "The link works, for this username")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "INVALID_CODE: an unknown link")
  @ApiResponse(
      responseCode = "410",
      description = "CODE_EXPIRED: over 30 minutes old; CODE_USED: it changed a password already")
  @PostMapping("/auth/password-reset/check")
  PasswordResetCheckResponse checkPasswordReset(
      @Valid @RequestBody PasswordResetCheckRequest request);

  @Operation(
      summary = "Set a new password with a reset link",
      description =
          "Public. Uses the link up and ends every session of the account, the website's and the"
              + " extension's; the user signs in again with the new password.")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "The password is changed")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "INVALID_CODE: an unknown link")
  @ApiResponse(
      responseCode = "410",
      description = "CODE_EXPIRED: over 30 minutes old; CODE_USED: it changed a password already")
  @PostMapping("/auth/password-reset/confirm")
  ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request);

  @Operation(summary = "Change the full name, username and date of birth")
  @ApiResponse(responseCode = "200", description = "The updated user")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "409", description = "USERNAME_TAKEN, in any case")
  @PutMapping("/me")
  MeResponse updateProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProfileRequest request);

  @Operation(
      summary = "Upload a new profile photo",
      description =
          "A JPEG, PNG or WebP image of at most 2 MB, recognized by its bytes; the website sends"
              + " the cropped 512x512 JPEG. It replaces the old photo, which is deleted.")
  @ApiResponse(responseCode = "200", description = "The user, with photoUrl")
  @ApiResponse(responseCode = "400", description = "INVALID_PHOTO: not a JPEG, PNG or WebP image")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE: over 2 MB")
  @PutMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  MeResponse changePhoto(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestPart("photo") MultipartFile photo);

  @Operation(summary = "Remove the profile photo")
  @ApiResponse(responseCode = "204", description = "Removed; the initial shows instead")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @DeleteMapping("/me/photo")
  ResponseEntity<Void> removePhoto(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);
}
