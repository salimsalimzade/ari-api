package app.ari.api.feature.account.application.facade;

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
import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.ExtensionCodeResponse;
import app.ari.api.feature.account.application.model.response.ExtensionTokensResponse;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.PasswordResetCheckResponse;
import app.ari.api.feature.account.application.model.response.SignedIn;
import app.ari.api.feature.account.application.model.response.UsernameAvailabilityResponse;
import java.util.UUID;

/** Sign-in use cases and the signed-in user. Other features use only this facade. */
public interface AccountFacade {

  /** Creates a password account and signs it in; throws {@code UsernameTakenException}. */
  SignedIn register(RegisterRequest request);

  /** Throws {@code InvalidCredentialsException} for every mismatch. */
  SignedIn login(LoginRequest request);

  /** Throws {@code SessionExpiredException} when the token is missing, unknown or used up. */
  AuthTokens refresh(String refreshToken);

  /** Ends the session; a missing or unknown token is ignored. */
  void logout(String refreshToken);

  /**
   * A one-time code for the signed-in website to hand to the browser extension (F13); throws {@code
   * SessionExpiredException} when the user no longer exists.
   */
  ExtensionCodeResponse extensionCode(UUID userId);

  /**
   * A one-time code for the extension to sign in as another account than the website's; throws
   * {@code InvalidCredentialsException} for every mismatch, like {@link #login}.
   */
  ExtensionCodeResponse extensionCode(LoginRequest request);

  /**
   * Swaps the code for the extension's own session; throws {@code InvalidExtensionCodeException}.
   */
  ExtensionTokensResponse connectExtension(ExtensionCodeRequest request);

  /** Throws {@code ExtensionSessionExpiredException} when the token is unknown or used up. */
  ExtensionTokensResponse refreshExtension(ExtensionRefreshRequest request);

  /** Ends the extension's session; an unknown token is ignored. */
  void logoutExtension(ExtensionRefreshRequest request);

  /** Throws {@code SessionExpiredException} when the user no longer exists. */
  MeResponse me(UUID userId);

  /**
   * Makes the email the pending one and queues its verify link in {@code language}. Throws {@code
   * EmailRateLimitedException} over 1 link a minute or 5 a day. Nothing is sent when it already is
   * the verified email.
   */
  MeResponse changeEmail(UUID userId, EmailRequest request, String language);

  /**
   * Queues a new link for the pending email; throws {@code NoPendingEmailException} without one.
   */
  MeResponse resendVerification(UUID userId, String language);

  /** Clears the verified and the pending email. */
  void removeEmail(UUID userId);

  /** Throws {@code InvalidEmailCodeException} or {@code EmailTakenException}. */
  void verifyEmail(VerifyEmailRequest request);

  /**
   * Queues a reset link, in {@code language}, to the verified email of the account with the
   * request's username or email. Throws {@code AccountNotFoundException}, {@code NoEmailException},
   * {@code EmailNotConfirmedException}, or {@code EmailRateLimitedException} over 20 requests an
   * hour from {@code clientAddress} or the account's email limits (1 a minute, 5 a day).
   */
  void requestPasswordReset(PasswordResetRequest request, String language, String clientAddress);

  /**
   * Whose password the link changes, without using it up. Throws {@code InvalidResetCodeException},
   * {@code ResetCodeExpiredException} or {@code ResetCodeUsedException}.
   */
  PasswordResetCheckResponse checkPasswordReset(PasswordResetCheckRequest request);

  /**
   * Sets the new password, uses the link up and ends every session of the account; throws what
   * {@link #checkPasswordReset} throws.
   */
  void resetPassword(PasswordResetConfirmRequest request);

  /** Changes the full name, username and date of birth; throws {@code UsernameTakenException}. */
  MeResponse updateProfile(UUID userId, ProfileRequest request);

  /**
   * Makes the image the profile photo; throws {@code InvalidPhotoException} unless it is a JPEG,
   * PNG or WebP image of at most 2 MB.
   */
  MeResponse changePhoto(UUID userId, byte[] photo);

  void removePhoto(UUID userId);

  /**
   * Adds {@code delta} (negative to take away) to the user's saves count, the Library count in the
   * sidebar. Call it in the same transaction as the save it counts, so the two never drift.
   */
  void changeSavesCount(UUID userId, int delta);

  /**
   * Whether anybody has the username, for the live check while typing one; throws {@code
   * UsernameCheckRateLimitedException} over 30 checks a minute from {@code clientAddress}.
   */
  UsernameAvailabilityResponse checkUsername(String username, String clientAddress);

  /** Fills the username Bloom filter when it is missing; returns how many it holds, or -1. */
  int fillUsernameFilterIfMissing();

  /** Builds the username Bloom filter again; returns how many it holds. */
  int rebuildUsernameFilter();
}
