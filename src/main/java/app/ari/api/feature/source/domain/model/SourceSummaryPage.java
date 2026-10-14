package app.ari.api.feature.source.domain.model;

import java.util.List;

/** One page of the Sources page; {@code next} is null on the last page. */
public record SourceSummaryPage(List<SourceSummary> items, SourceCursor next) {}
