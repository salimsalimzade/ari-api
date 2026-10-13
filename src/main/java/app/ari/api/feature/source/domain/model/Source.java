package app.ari.api.feature.source.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A web page or an imported file that saves come from. {@code name} is the site's name (or the
 * file's), {@code title} the page's or document's. {@code url} is the page's address without its
 * fragment, set for a {@link SourceKind#WEBSITE} and null for a {@link SourceKind#FILE}.
 */
public record Source(
    UUID id,
    UUID userId,
    SourceKind kind,
    String name,
    String title,
    String author,
    String url,
    Instant createdAt,
    Instant updatedAt) {}
