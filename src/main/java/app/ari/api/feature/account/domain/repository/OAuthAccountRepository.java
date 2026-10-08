package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.AuthProvider;
import app.ari.api.feature.account.domain.model.OAuthAccount;
import java.util.Optional;

/** Stores links between users and provider accounts. */
public interface OAuthAccountRepository {

  Optional<OAuthAccount> findByProviderUserId(AuthProvider provider, String providerUserId);

  /**
   * Adds the link; returns false and changes nothing when that provider account is already linked,
   * or the user already has an account at that provider.
   */
  boolean insert(OAuthAccount account);
}
