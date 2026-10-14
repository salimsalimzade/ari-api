package app.ari.api.feature.fileimport.application.model.response;

import java.util.UUID;

/** What saving a review changed in the Library. */
public record ReviewResponse(int added, int removed, UUID sourceId) {}
