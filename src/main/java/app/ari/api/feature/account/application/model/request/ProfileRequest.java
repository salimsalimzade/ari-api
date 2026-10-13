package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of {@code PUT /api/v1/me}; the same rules as sign-up. */
public record ProfileRequest(
    @Schema(
            description = "3 to 30 letters, digits, underscores or dots; stored in lowercase",
            example = "ada.lovelace")
        @NotBlank @Pattern(
            regexp = "[A-Za-z0-9_.]{3,30}",
            message = "must be 3 to 30 letters, digits, underscores or dots")
        String username,
    @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 150) String fullName,
    @Schema(description = "ISO date, in the past", example = "1815-12-10") @NotNull @Past LocalDate birthDate) {}
