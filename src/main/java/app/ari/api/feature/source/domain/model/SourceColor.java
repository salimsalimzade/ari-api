package app.ari.api.feature.source.domain.model;

import java.util.UUID;

/**
 * A color saves from a source use. Colors belong to the save feature, so this one is its stored
 * value read as text: YELLOW, GREEN, BLUE, PINK or PURPLE.
 */
public record SourceColor(UUID sourceId, String color) {}
