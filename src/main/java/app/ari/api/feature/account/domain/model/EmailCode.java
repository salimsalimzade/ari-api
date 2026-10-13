package app.ari.api.feature.account.domain.model;

import java.util.UUID;

/** What a verify link stands for: this user owns this email. */
public record EmailCode(UUID userId, String email) {}
