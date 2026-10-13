package app.ari.api.feature.source.application.model.response;

import app.ari.api.feature.source.domain.model.SourceKind;
import java.util.UUID;

/** Where a save came from. {@code url} is left out for a file, {@code author} when unknown. */
public record SourceResponse(
    UUID id, SourceKind kind, String name, String title, String author, String url) {}
