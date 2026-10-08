package app.ari.api.feature.account.presentation.impl;

import app.ari.api.feature.account.application.exception.unauthorized.SessionExpiredException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.SignedIn;
import app.ari.api.feature.account.presentation.AccountController;
import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountControllerImpl implements AccountController {

  private final AccountFacade facade;
  private final AuthCookies cookies;

  public AccountControllerImpl(AccountFacade facade, AuthCookies cookies) {
    this.facade = facade;
    this.cookies = cookies;
  }

  @Override
  public ResponseEntity<MeResponse> register(@Valid RegisterRequest request) {
    SignedIn signedIn = facade.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .headers(cookies.signIn(signedIn.tokens()))
        .body(signedIn.me());
  }

  @Override
  public ResponseEntity<MeResponse> login(@Valid LoginRequest request) {
    SignedIn signedIn = facade.login(request);
    return ResponseEntity.ok().headers(cookies.signIn(signedIn.tokens())).body(signedIn.me());
  }

  @Override
  public ResponseEntity<Void> refresh(String refreshToken) {
    return ResponseEntity.noContent().headers(cookies.signIn(facade.refresh(refreshToken))).build();
  }

  @Override
  public ResponseEntity<Void> logout(String refreshToken) {
    facade.logout(refreshToken);
    return ResponseEntity.noContent().headers(cookies.clear()).build();
  }

  @Override
  public MeResponse me(Jwt jwt) {
    return facade.me(UUID.fromString(jwt.getSubject()));
  }

  /** The session can't continue: answer 401 and delete both cookies, which only we can do. */
  @ExceptionHandler(SessionExpiredException.class)
  public ResponseEntity<ProblemDetail> handleSessionExpired(SessionExpiredException ex) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    body.setProperty("code", ex.getCode());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(cookies.clear()).body(body);
  }
}
