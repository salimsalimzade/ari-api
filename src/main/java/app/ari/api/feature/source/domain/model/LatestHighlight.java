package app.ari.api.feature.source.domain.model;

import java.util.UUID;

/** The newest save from a source that has a color: its words, and the color as text. */
public record LatestHighlight(UUID sourceId, String text, String color) {}
