package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/password-reset}. */
public record PasswordResetRequest(
    @Schema(description = "A username, or an account's verified email", example = "ada.lovelace")
        @NotBlank @Size(max = 254) String login) {}
