package app.ari.api.feature.account.application.model.request;

import app.ari.api.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/password-reset/confirm}. */
public record PasswordResetConfirmRequest(
    @Schema(description = "The code from the link in the email") @NotBlank @Size(max = 100) String code,
    @Schema(
            description = "The new password: 8 characters to 72 bytes, as at sign-up",
            example = "correct horse")
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") @MaxBytes(72)
        String password) {}
