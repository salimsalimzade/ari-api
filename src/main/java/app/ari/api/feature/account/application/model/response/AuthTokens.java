package app.ari.api.feature.account.application.model.response;

import java.time.Duration;

/**
 * A token pair for the presentation layer to put into cookies; never sent as JSON. {@code
 * expiresIn} is the access token's lifetime, which its cookie follows.
 */
public record AuthTokens(String accessToken, String refreshToken, Duration expiresIn) {}
