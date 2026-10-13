package app.ari.api.feature.save.infrastructure;

import app.ari.api.feature.save.domain.repository.SaveRepository;
import org.apache.ibatis.annotations.Mapper;

/** PostgreSQL adapter for {@link SaveRepository}; SQL in persistence/SaveMyBatisRepository.xml. */
@Mapper
public interface SaveMyBatisRepository extends SaveRepository {}
