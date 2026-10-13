package app.ari.api.feature.save.domain.model;

import java.util.List;

/** One page of the Library; {@code next} is null on the last page. */
public record SavePage(List<TaggedSave> items, SaveCursor next) {}
