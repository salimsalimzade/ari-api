package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.repository.UserRepository;
import org.apache.ibatis.annotations.Mapper;

/** PostgreSQL adapter for {@link UserRepository}; SQL in persistence/UserMyBatisRepository.xml. */
@Mapper
public interface UserMyBatisRepository extends UserRepository {}
