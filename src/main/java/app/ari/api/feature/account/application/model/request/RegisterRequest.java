package app.ari.api.feature.account.application.model.request;

import app.ari.api.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/register}. */
public record RegisterRequest(
    @Schema(example = "ada@example.com") @NotBlank @Email @Size(max = 320) String email,
    @Schema(description = "8 characters to 72 bytes (bcrypt's limit)", example = "correct horse")
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") @MaxBytes(72)
        String password,
    @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 100) String displayName) {}
