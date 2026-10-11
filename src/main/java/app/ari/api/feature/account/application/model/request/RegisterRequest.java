package app.ari.api.feature.account.application.model.request;

import app.ari.api.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of {@code POST /api/v1/auth/register}. */
public record RegisterRequest(
    @Schema(example = "ada@example.com") @NotBlank @Email @Size(max = 320) String email,
    @Schema(
            description = "3 to 30 letters, digits, underscores or dots; stored in lowercase",
            example = "ada.lovelace")
        @NotBlank @Pattern(
            regexp = "[A-Za-z0-9_.]{3,30}",
            message = "must be 3 to 30 letters, digits, underscores or dots")
        String username,
    @Schema(description = "8 characters to 72 bytes (bcrypt's limit)", example = "correct horse")
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") @MaxBytes(72)
        String password,
    @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 150) String fullName,
    @Schema(description = "ISO date, in the past", example = "1815-12-10") @NotNull @Past LocalDate birthDate) {}
