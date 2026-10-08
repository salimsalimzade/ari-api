package app.ari.api.feature.account.domain.model;

/** A new token pair: the signed JWT access token and the raw refresh token. */
public record IssuedTokens(String accessToken, String refreshToken) {}
