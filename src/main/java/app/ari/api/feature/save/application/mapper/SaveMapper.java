package app.ari.api.feature.save.application.mapper;

import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import app.ari.api.feature.save.domain.model.NewSave;
import app.ari.api.feature.save.domain.model.SaveChange;
import app.ari.api.feature.save.domain.model.SaveFacets;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.TaggedSave;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SaveMapper {

  NewSave toNewSave(CreateSaveRequest request);

  SaveChange toChange(UpdateSaveRequest request);

  @Mapping(target = "kind", source = "type")
  @Mapping(target = "tags", source = "tag")
  @Mapping(target = "colors", source = "color")
  @Mapping(target = "sourceIds", source = "source")
  @Mapping(target = "query", source = "q")
  @Mapping(target = "sourcesMatchingQuery", ignore = true)
  SaveFilter toFilter(SaveFilterRequest request);

  @Mapping(target = "kind", source = "type")
  @Mapping(target = "tags", source = "tag")
  @Mapping(target = "colors", source = "color")
  @Mapping(target = "sourceIds", source = "source")
  @Mapping(target = "query", source = "q")
  @Mapping(target = "sourcesMatchingQuery", ignore = true)
  SaveFilter toFilter(ListSavesRequest request);

  /** {@code imageUrl} is the signed link to an image clip's picture, or null for any other save. */
  @Mapping(target = ".", source = "tagged.save")
  @Mapping(target = "tags", source = "tagged.tags")
  @Mapping(target = "source", source = "source")
  @Mapping(target = "imageUrl", source = "imageUrl")
  SaveResponse toResponse(TaggedSave tagged, SourceResponse source, String imageUrl);

  /** {@code sources} holds every source the facets count, by id. */
  default FacetsResponse toFacetsResponse(SaveFacets facets, Map<UUID, SourceResponse> sources) {
    List<FacetsResponse.TypeCount> types =
        facets.kinds().entrySet().stream()
            .map(entry -> new FacetsResponse.TypeCount(entry.getKey(), entry.getValue()))
            .toList();
    List<FacetsResponse.SourceFacet> sourceFacets =
        facets.sources().stream()
            .filter(count -> sources.containsKey(count.sourceId()))
            .map(
                count ->
                    new FacetsResponse.SourceFacet(
                        count.sourceId(), sources.get(count.sourceId()).name(), count.count()))
            .sorted(
                Comparator.comparingInt(FacetsResponse.SourceFacet::count)
                    .reversed()
                    .thenComparing(FacetsResponse.SourceFacet::name))
            .toList();
    return new FacetsResponse(
        types.stream().mapToInt(FacetsResponse.TypeCount::count).sum(),
        types,
        facets.tags().stream()
            .map(tag -> new FacetsResponse.TagFacet(tag.name(), tag.count()))
            .toList(),
        facets.colors().entrySet().stream()
            .map(entry -> new FacetsResponse.ColorFacet(entry.getKey(), entry.getValue()))
            .toList(),
        sourceFacets);
  }
}
