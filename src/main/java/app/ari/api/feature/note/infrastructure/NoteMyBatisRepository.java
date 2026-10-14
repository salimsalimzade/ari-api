package app.ari.api.feature.note.infrastructure;

import app.ari.api.feature.note.domain.repository.NoteRepository;
import org.apache.ibatis.annotations.Mapper;

/** PostgreSQL adapter for {@link NoteRepository}; SQL in persistence/NoteMyBatisRepository.xml. */
@Mapper
public interface NoteMyBatisRepository extends NoteRepository {}
