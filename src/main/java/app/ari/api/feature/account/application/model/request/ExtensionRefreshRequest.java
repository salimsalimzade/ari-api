package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/extension/refresh} and {@code /logout}. */
public record ExtensionRefreshRequest(
    @Schema(description = "The extension's current refresh token") @NotBlank @Size(max = 100) String refreshToken) {}
