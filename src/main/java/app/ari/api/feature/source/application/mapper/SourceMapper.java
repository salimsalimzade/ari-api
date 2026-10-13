package app.ari.api.feature.source.application.mapper;

import app.ari.api.feature.source.application.model.response.SourceResponse;
import app.ari.api.feature.source.domain.model.Source;
import org.mapstruct.Mapper;

@Mapper
public interface SourceMapper {

  SourceResponse toResponse(Source source);
}
