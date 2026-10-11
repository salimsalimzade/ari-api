package app.ari.api.feature.account.application.facade.impl;

import app.ari.api.feature.account.application.exception.conflict.EmailTakenException;
import app.ari.api.feature.account.application.exception.conflict.UsernameTakenException;
import app.ari.api.feature.account.application.exception.unauthorized.GoogleSignInRejectedException;
import app.ari.api.feature.account.application.exception.unauthorized.InvalidCredentialsException;
import app.ari.api.feature.account.application.exception.unauthorized.SessionExpiredException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.mapper.AccountMapper;
import app.ari.api.feature.account.application.model.request.GoogleProfile;
import app.ari.api.feature.account.application.model.request.LoginRequest;
import app.ari.api.feature.account.application.model.request.RegisterRequest;
import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.application.model.response.SignedIn;
import app.ari.api.feature.account.domain.model.TokenClient;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.service.AccountService;
import app.ari.api.feature.account.domain.service.TokenService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Sign-in use cases. Register, login and Google sign-in each run in one transaction, so a user is
 * never saved without its session. Refresh and logout get theirs inside {@link TokenService}: a
 * revoked session must stay revoked even though the request then fails with a 401.
 */
@Service
public class AccountFacadeImpl implements AccountFacade {

  private final AccountService accountService;
  private final TokenService tokenService;
  private final AccountMapper mapper;

  public AccountFacadeImpl(
      AccountService accountService, TokenService tokenService, AccountMapper mapper) {
    this.accountService = accountService;
    this.tokenService = tokenService;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public SignedIn register(RegisterRequest request) {
    User user =
        accountService
            .register(
                request.email(),
                request.username(),
                request.password(),
                request.fullName(),
                request.birthDate())
            .orElseThrow(
                () ->
                    accountService.usernameTaken(request.username())
                        ? new UsernameTakenException()
                        : new EmailTakenException());
    return signIn(user);
  }

  @Override
  @Transactional
  public SignedIn login(LoginRequest request) {
    User user =
        accountService
            .authenticate(request.email(), request.password())
            .orElseThrow(InvalidCredentialsException::new);
    return signIn(user);
  }

  @Override
  public AuthTokens refresh(String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      throw new SessionExpiredException();
    }
    return tokenService
        .rotate(refreshToken)
        .map(mapper::toAuthTokens)
        .orElseThrow(SessionExpiredException::new);
  }

  @Override
  public void logout(String refreshToken) {
    if (StringUtils.hasText(refreshToken)) {
      tokenService.revokeSession(refreshToken);
    }
  }

  @Override
  public MeResponse me(UUID userId) {
    return accountService
        .findById(userId)
        .map(mapper::toMeResponse)
        .orElseThrow(SessionExpiredException::new);
  }

  @Override
  @Transactional
  public AuthTokens signInWithGoogle(GoogleProfile profile) {
    User user =
        accountService
            .signInWithGoogle(
                profile.subject(), profile.email(), profile.emailVerified(), profile.name())
            .orElseThrow(GoogleSignInRejectedException::new);
    return mapper.toAuthTokens(tokenService.issue(user, TokenClient.WEB));
  }

  private SignedIn signIn(User user) {
    AuthTokens tokens = mapper.toAuthTokens(tokenService.issue(user, TokenClient.WEB));
    return new SignedIn(mapper.toMeResponse(user), tokens);
  }
}
