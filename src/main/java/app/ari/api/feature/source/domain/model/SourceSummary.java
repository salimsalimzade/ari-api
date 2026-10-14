package app.ari.api.feature.source.domain.model;

import java.util.List;

/**
 * A source with what is saved from it, as the Sources page shows it. {@code colors} are the ones
 * its saves use, most used first; {@code latestHighlight} is null when none of them has a color.
 * {@code notes} quote its saves, the one changed most recently first.
 */
public record SourceSummary(
    Source source,
    SourceStats stats,
    List<String> colors,
    LatestHighlight latestHighlight,
    List<SourceNote> notes) {}
