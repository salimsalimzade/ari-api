package app.ari.api.feature.source.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * What the saves from a source add up to: how many there are of each kind, and when the first and
 * the latest were made. Only a source with saves has them.
 */
public record SourceStats(
    UUID sourceId,
    int savesCount,
    int highlightsCount,
    int clipsCount,
    int importsCount,
    Instant firstSavedAt,
    Instant lastSavedAt) {}
