package app.ari.api.feature.save.infrastructure;

import app.ari.api.feature.save.domain.repository.TagRepository;
import org.apache.ibatis.annotations.Mapper;

/** PostgreSQL adapter for {@link TagRepository}; SQL in persistence/TagMyBatisRepository.xml. */
@Mapper
public interface TagMyBatisRepository extends TagRepository {}
