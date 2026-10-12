package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/login}. The examples are the local demo user. */
public record LoginRequest(
    @Schema(example = "adalovelace") @NotBlank String username,
    @Schema(example = "i<3you@*") @NotBlank String password) {}
