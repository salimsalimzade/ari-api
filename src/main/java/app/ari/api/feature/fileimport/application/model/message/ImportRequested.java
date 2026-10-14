package app.ari.api.feature.fileimport.application.model.message;

import java.util.UUID;

/** The {@code import.process} message: read this import's file. */
public record ImportRequested(UUID importId) {}
