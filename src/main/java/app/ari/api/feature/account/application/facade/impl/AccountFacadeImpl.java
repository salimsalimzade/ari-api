package app.ari.api.feature.account.application.facade.impl;

import app.ari.api.common.client.Queues;
import app.ari.api.config.WebProperties;
import app.ari.api.feature.account.application.exception.badrequest.InvalidPhotoException;
import app.ari.api.feature.account.application.exception.badrequest.NoPendingEmailException;
import app.ari.api.feature.account.application.exception.conflict.EmailNotConfirmedException;
import app.ari.api.feature.account.application.exception.conflict.EmailTakenException;
import app.ari.api.feature.account.application.exception.conflict.NoEmailException;
import app.ari.api.feature.account.application.exception.conflict.UsernameTakenException;
import app.ari.api.feature.account.application.exception.gone.ResetCodeExpiredException;
import app.ari.api.feature.account.application.exception.gone.ResetCodeUsedException;
import app.ari.api.feature.account.application.exception.notfound.AccountNotFoundException;
import app.ari.api.feature.account.application.exception.toomanyrequests.EmailRateLimitedException;
import app.ari.api.feature.account.application.exception.toomanyrequests.UsernameCheckRateLimitedException;
import app.ari.api.feature.account.application.exception.unauthorized.ExtensionSessionExpiredException;
import app.ari.api.feature.account.application.exception.unauthorized.InvalidCredentialsException;
import app.ari.api.feature.account.application.exception.unauthorized.InvalidEmailCodeException;
import app.ari.api.feature.account.application.exception.unauthorized.InvalidExtensionCodeException;
import app.ari.api.feature.account.application.exception.unauthorized.InvalidResetCodeException;
import app.ari.api.feature.account.application.exception.unauthorized.SessionExpiredException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.mapper.AccountMapper;
import app.ari.api.feature.account.application.model.message.ResetPasswordMessage;
import app.ari.api.feature.account.application.model.message.VerifyEmailMessage;
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
import app.ari.api.feature.account.domain.model.ResetLink;
import app.ari.api.feature.account.domain.model.ResetRecipient;
import app.ari.api.feature.account.domain.model.TokenClient;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.service.AccountService;
import app.ari.api.feature.account.domain.service.EmailService;
import app.ari.api.feature.account.domain.service.PasswordResetService;
import app.ari.api.feature.account.domain.service.PhotoService;
import app.ari.api.feature.account.domain.service.TokenService;
import app.ari.api.feature.account.domain.service.UsernameService;
import app.ari.api.feature.outbox.application.facade.OutboxFacade;
import app.ari.api.feature.outbox.application.model.request.NewOutboxEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sign-in use cases. Register and login each run in one transaction, so a user is never saved
 * without its session. Refresh, logout and the extension's sign-in get theirs inside {@link
 * TokenService}: a revoked session must stay revoked even though the request then fails with a 401.
 * An email's verify link is queued in the outbox in the same transaction as the pending email, and
 * a password's reset link with its request. The photo use cases are not one transaction: the file
 * goes to storage first, then a single row update points at it.
 */
@Service
public class AccountFacadeImpl implements AccountFacade {

  private final AccountService accountService;
  private final TokenService tokenService;
  private final EmailService emailService;
  private final PhotoService photoService;
  private final UsernameService usernameService;
  private final PasswordResetService passwordResetService;
  private final OutboxFacade outbox;
  private final WebProperties web;
  private final AccountMapper mapper;

  public AccountFacadeImpl(
      AccountService accountService,
      TokenService tokenService,
      EmailService emailService,
      PhotoService photoService,
      UsernameService usernameService,
      PasswordResetService passwordResetService,
      OutboxFacade outbox,
      WebProperties web,
      AccountMapper mapper) {
    this.accountService = accountService;
    this.tokenService = tokenService;
    this.emailService = emailService;
    this.photoService = photoService;
    this.usernameService = usernameService;
    this.passwordResetService = passwordResetService;
    this.outbox = outbox;
    this.web = web;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public SignedIn register(RegisterRequest request) {
    User user =
        accountService
            .register(
                request.username(), request.password(), request.fullName(), request.birthDate())
            .orElseThrow(UsernameTakenException::new);
    // A rolled-back insert only leaves a name the database check then finds free.
    usernameService.remember(user.username());
    return signIn(user);
  }

  @Override
  @Transactional
  public SignedIn login(LoginRequest request) {
    User user =
        accountService
            .authenticate(request.username(), request.password())
            .orElseThrow(InvalidCredentialsException::new);
    return signIn(user);
  }

  @Override
  public AuthTokens refresh(String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      throw new SessionExpiredException();
    }
    return tokenService
        .rotate(refreshToken, TokenClient.WEB)
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
  public ExtensionCodeResponse extensionCode(UUID userId) {
    return new ExtensionCodeResponse(tokenService.issueExtensionCode(currentUser(userId).id()));
  }

  @Override
  public ExtensionCodeResponse extensionCode(LoginRequest request) {
    User user =
        accountService
            .authenticate(request.username(), request.password())
            .orElseThrow(InvalidCredentialsException::new);
    return new ExtensionCodeResponse(tokenService.issueExtensionCode(user.id()));
  }

  @Override
  public ExtensionTokensResponse connectExtension(ExtensionCodeRequest request) {
    return tokenService
        .redeemExtensionCode(request.code())
        .map(mapper::toExtensionTokens)
        .orElseThrow(InvalidExtensionCodeException::new);
  }

  @Override
  public ExtensionTokensResponse refreshExtension(ExtensionRefreshRequest request) {
    return tokenService
        .rotate(request.refreshToken(), TokenClient.EXTENSION)
        .map(mapper::toExtensionTokens)
        .orElseThrow(ExtensionSessionExpiredException::new);
  }

  @Override
  public void logoutExtension(ExtensionRefreshRequest request) {
    tokenService.revokeSession(request.refreshToken());
  }

  @Override
  public MeResponse me(UUID userId) {
    return toMe(currentUser(userId));
  }

  @Override
  @Transactional
  public MeResponse changeEmail(UUID userId, EmailRequest request, String language) {
    User user = currentUser(userId);
    if (!emailService.mayRequestLink(userId)) {
      throw new EmailRateLimitedException();
    }
    Optional<String> code = emailService.requestVerification(user, request.email());
    User changed = currentUser(userId);
    code.ifPresent(value -> queueVerifyEmail(changed, value, language));
    return toMe(changed);
  }

  @Override
  @Transactional
  public MeResponse resendVerification(UUID userId, String language) {
    User user = currentUser(userId);
    if (user.pendingEmail() == null) {
      throw new NoPendingEmailException();
    }
    if (!emailService.mayRequestLink(userId)) {
      throw new EmailRateLimitedException();
    }
    String code = emailService.resendVerification(user).orElseThrow(NoPendingEmailException::new);
    queueVerifyEmail(user, code, language);
    return toMe(user);
  }

  @Override
  @Transactional
  public void removeEmail(UUID userId) {
    emailService.removeEmail(currentUser(userId).id());
  }

  @Override
  @Transactional
  public void verifyEmail(VerifyEmailRequest request) {
    switch (emailService.confirm(request.code())) {
      case CONFIRMED -> {}
      case INVALID_CODE -> throw new InvalidEmailCodeException();
      case EMAIL_TAKEN -> throw new EmailTakenException();
    }
  }

  @Override
  @Transactional
  public void requestPasswordReset(
      PasswordResetRequest request, String language, String clientAddress) {
    // Before the lookup, so nobody can try many usernames or emails.
    if (!passwordResetService.mayRequest(clientAddress)) {
      throw new EmailRateLimitedException();
    }
    ResetRecipient recipient = passwordResetService.recipient(request.login());
    User user =
        switch (recipient.status()) {
          case FOUND -> recipient.user();
          case NO_ACCOUNT -> throw new AccountNotFoundException();
          case NO_EMAIL -> throw new NoEmailException();
          case EMAIL_NOT_CONFIRMED -> throw new EmailNotConfirmedException();
        };
    // The same limits as verify links: the account's emails share them.
    if (!emailService.mayRequestLink(user.id())) {
      throw new EmailRateLimitedException();
    }
    String link =
        UriComponentsBuilder.fromUriString(web.url())
            .path("/reset-password")
            .queryParam("code", passwordResetService.newCode(user.id()))
            .toUriString();
    ResetPasswordMessage message =
        new ResetPasswordMessage(
            user.email(), user.fullName(), user.username(), languageOf(language), link);
    outbox.add(
        new NewOutboxEvent(
            "USER", user.id(), "ResetPassword", Queues.EMAIL_TRANSACTIONAL, message));
  }

  @Override
  public PasswordResetCheckResponse checkPasswordReset(PasswordResetCheckRequest request) {
    return new PasswordResetCheckResponse(
        validUser(passwordResetService.check(request.code())).username());
  }

  @Override
  @Transactional
  public void resetPassword(PasswordResetConfirmRequest request) {
    validUser(passwordResetService.reset(request.code(), request.password()));
  }

  @Override
  @Transactional
  public MeResponse updateProfile(UUID userId, ProfileRequest request) {
    String before = currentUser(userId).username();
    User user =
        accountService
            .updateProfile(userId, request.username(), request.fullName(), request.birthDate())
            .orElseThrow(UsernameTakenException::new);
    if (!user.username().equals(before)) {
      usernameService.remember(user.username());
    }
    return toMe(user);
  }

  @Override
  public UsernameAvailabilityResponse checkUsername(String username, String clientAddress) {
    if (!usernameService.mayCheck(clientAddress)) {
      throw new UsernameCheckRateLimitedException();
    }
    String normalized = Usernames.normalize(username);
    return new UsernameAvailabilityResponse(normalized, usernameService.isAvailable(normalized));
  }

  @Override
  public int fillUsernameFilterIfMissing() {
    return usernameService.fillFilterIfMissing();
  }

  @Override
  public int rebuildUsernameFilter() {
    return usernameService.rebuildFilter();
  }

  @Override
  public MeResponse changePhoto(UUID userId, byte[] photo) {
    return toMe(
        photoService
            .changePhoto(currentUser(userId), photo)
            .orElseThrow(InvalidPhotoException::new));
  }

  @Override
  public void removePhoto(UUID userId) {
    photoService.removePhoto(currentUser(userId));
  }

  @Override
  @Transactional
  public void changeSavesCount(UUID userId, int delta) {
    accountService.changeSavesCount(userId, delta);
  }

  /** The link's user; throws the error that says why the link doesn't work. */
  private static User validUser(ResetLink link) {
    return switch (link.status()) {
      case VALID -> link.user();
      case EXPIRED -> throw new ResetCodeExpiredException();
      case USED -> throw new ResetCodeUsedException();
      case INVALID -> throw new InvalidResetCodeException();
    };
  }

  private MeResponse toMe(User user) {
    return mapper.toMeResponse(user, photoService.photoUrl(user).orElse(null));
  }

  private User currentUser(UUID userId) {
    return accountService.findById(userId).orElseThrow(SessionExpiredException::new);
  }

  private void queueVerifyEmail(User user, String code, String language) {
    String link =
        UriComponentsBuilder.fromUriString(web.url())
            .path("/verify-email")
            .queryParam("code", code)
            .toUriString();
    VerifyEmailMessage message =
        new VerifyEmailMessage(user.pendingEmail(), user.fullName(), languageOf(language), link);
    outbox.add(
        new NewOutboxEvent("USER", user.id(), "VerifyEmail", Queues.EMAIL_TRANSACTIONAL, message));
  }

  /** The website's languages; anything else gets English. */
  private static String languageOf(String language) {
    return "az".equals(language) ? "az" : "en";
  }

  private SignedIn signIn(User user) {
    AuthTokens tokens = mapper.toAuthTokens(tokenService.issue(user, TokenClient.WEB));
    return new SignedIn(toMe(user), tokens);
  }
}
