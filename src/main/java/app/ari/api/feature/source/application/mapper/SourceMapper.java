package app.ari.api.feature.source.application.mapper;

import app.ari.api.feature.source.application.model.response.SourceCountsResponse;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import app.ari.api.feature.source.application.model.response.SourceSummaryResponse;
import app.ari.api.feature.source.domain.model.Source;
import app.ari.api.feature.source.domain.model.SourceKind;
import app.ari.api.feature.source.domain.model.SourceNote;
import app.ari.api.feature.source.domain.model.SourceSummary;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SourceMapper {

  SourceResponse toResponse(Source source);

  @Mapping(target = ".", source = "source")
  @Mapping(target = ".", source = "stats")
  SourceSummaryResponse toSummaryResponse(SourceSummary summary);

  @Mapping(target = "id", source = "noteId")
  SourceSummaryResponse.QuotingNote toQuotingNote(SourceNote note);

  /** {@code counts} has every kind. */
  default SourceCountsResponse toCountsResponse(Map<SourceKind, Integer> counts) {
    List<SourceCountsResponse.KindCount> kinds =
        counts.entrySet().stream()
            .map(entry -> new SourceCountsResponse.KindCount(entry.getKey(), entry.getValue()))
            .toList();
    return new SourceCountsResponse(
        kinds.stream().mapToInt(SourceCountsResponse.KindCount::count).sum(), kinds);
  }
}
