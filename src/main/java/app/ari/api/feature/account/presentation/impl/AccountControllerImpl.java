package app.ari.api.feature.account.presentation.impl;

import app.ari.api.feature.account.application.exception.unauthorized.SessionExpiredException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.model.request.EmailRequest;
import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.ProfileRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.request.VerifyEmailRequest;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.SignedIn;
import app.ari.api.feature.account.application.model.response.UsernameAvailabilityResponse;
import app.ari.api.feature.account.presentation.AccountController;
import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
  public UsernameAvailabilityResponse checkUsername(String username, HttpServletRequest request) {
    return facade.checkUsername(username, request.getRemoteAddr());
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
    return facade.me(userId(jwt));
  }

  @Override
  public ResponseEntity<MeResponse> changeEmail(
      Jwt jwt, @Valid EmailRequest request, String language) {
    MeResponse me = facade.changeEmail(userId(jwt), request, language);
    return me.pendingEmail() != null ? ResponseEntity.accepted().body(me) : ResponseEntity.ok(me);
  }

  @Override
  public ResponseEntity<MeResponse> resendVerification(Jwt jwt, String language) {
    return ResponseEntity.accepted().body(facade.resendVerification(userId(jwt), language));
  }

  @Override
  public ResponseEntity<Void> removeEmail(Jwt jwt) {
    facade.removeEmail(userId(jwt));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> verifyEmail(@Valid VerifyEmailRequest request) {
    facade.verifyEmail(request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public MeResponse updateProfile(Jwt jwt, @Valid ProfileRequest request) {
    return facade.updateProfile(userId(jwt), request);
  }

  @Override
  public MeResponse changePhoto(Jwt jwt, MultipartFile photo) {
    try {
      return facade.changePhoto(userId(jwt), photo.getBytes());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the uploaded photo", e);
    }
  }

  @Override
  public ResponseEntity<Void> removePhoto(Jwt jwt) {
    facade.removePhoto(userId(jwt));
    return ResponseEntity.noContent().build();
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  /** The session can't continue: answer 401 and delete both cookies, which only we can do. */
  @ExceptionHandler(SessionExpiredException.class)
  public ResponseEntity<ProblemDetail> handleSessionExpired(SessionExpiredException ex) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    body.setProperty("code", ex.getCode());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(cookies.clear()).body(body);
  }
}
