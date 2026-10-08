package app.ari.api.common.handlers;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * Stores {@link Instant} in {@code timestamptz} columns as UTC, so the JVM and database time zones
 * never change a value. Replaces MyBatis' built-in handler, which goes through {@code Timestamp}.
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setObject(i, parameter.atOffset(ZoneOffset.UTC));
  }

  @Override
  public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toInstant(rs.getObject(columnName, OffsetDateTime.class));
  }

  @Override
  public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toInstant(rs.getObject(columnIndex, OffsetDateTime.class));
  }

  @Override
  public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toInstant(cs.getObject(columnIndex, OffsetDateTime.class));
  }

  private static Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
