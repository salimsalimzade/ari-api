package app.ari.api.feature.save.domain.model;

import java.util.List;

/** A save with the names of its tags, in alphabetical order. */
public record TaggedSave(Save save, List<String> tags) {}
