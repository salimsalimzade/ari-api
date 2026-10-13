package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/verify-email}. */
public record VerifyEmailRequest(
    @Schema(description = "The code from the link in the email") @NotBlank @Size(max = 100) String code) {}
