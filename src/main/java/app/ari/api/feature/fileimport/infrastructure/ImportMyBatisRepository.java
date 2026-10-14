package app.ari.api.feature.fileimport.infrastructure;

import app.ari.api.feature.fileimport.domain.repository.ImportRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostgreSQL adapter for {@link ImportRepository}; SQL in persistence/ImportMyBatisRepository.xml.
 */
@Mapper
public interface ImportMyBatisRepository extends ImportRepository {}
