package app.ari.api.feature.account.application.model.response;

/**
 * The extension's token pair, sent as JSON: it keeps both in its own storage, never in cookies.
 *
 * @param accessToken JWT for {@code Authorization: Bearer}, valid for 15 minutes
 * @param refreshToken for {@code POST /api/v1/auth/extension/refresh}, replaced on every refresh
 */
public record ExtensionTokensResponse(String accessToken, String refreshToken) {}
