package app.ari.api.feature.account.domain.model;

import java.time.Duration;
import java.util.UUID;

/**
 * A Supabase session: its access token (a JWT), its refresh token, which Supabase replaces on every
 * refresh, and how long the access token lasts.
 */
public record IssuedTokens(
    UUID userId, String accessToken, String refreshToken, Duration expiresIn) {}
