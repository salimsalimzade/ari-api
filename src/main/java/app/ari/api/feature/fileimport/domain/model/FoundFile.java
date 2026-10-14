package app.ari.api.feature.fileimport.domain.model;

import java.util.List;

/**
 * What a reader found in a file: the title and author it gives (null when it gives none) and its
 * marks in order.
 */
public record FoundFile(String title, String author, List<FoundMark> marks) {}
