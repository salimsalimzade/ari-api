package app.ari.api.feature.note.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * A note as the Notes page lists it: the saves it quotes, in order, and whether it is empty (no
 * title, no quote and no paragraph with anything in it).
 */
public record NoteSummary(Note note, List<UUID> saveIds, boolean empty) {}
