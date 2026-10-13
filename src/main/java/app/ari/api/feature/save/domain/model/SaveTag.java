package app.ari.api.feature.save.domain.model;

import java.util.UUID;

/** One tag on one save, for loading the tags of a page of saves at once. */
public record SaveTag(UUID saveId, String name) {}
