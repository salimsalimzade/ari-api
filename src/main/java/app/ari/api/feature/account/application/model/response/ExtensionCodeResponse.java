package app.ari.api.feature.account.application.model.response;

/**
 * Answer of {@code POST /api/v1/me/extension-code}.
 *
 * @param code signs the extension in once, within 60 seconds
 */
public record ExtensionCodeResponse(String code) {}
