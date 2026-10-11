package app.ari.api.feature.account.application.facade;

import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.SignedIn;
import java.util.UUID;

/** Sign-in use cases and the signed-in user. Other features use only this facade. */
public interface AccountFacade {

  /** Creates a password account and signs it in; throws {@code EmailTakenException}. */
  SignedIn register(RegisterRequest request);

  /** Throws {@code InvalidCredentialsException} for every mismatch. */
  SignedIn login(LoginRequest request);

  /** Throws {@code SessionExpiredException} when the token is missing, unknown or used up. */
  AuthTokens refresh(String refreshToken);

  /** Ends the session; a missing or unknown token is ignored. */
  void logout(String refreshToken);

  /** Throws {@code SessionExpiredException} when the user no longer exists. */
  MeResponse me(UUID userId);
}
