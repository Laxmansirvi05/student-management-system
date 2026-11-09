package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rules of the schema that every migration must keep (docs/08 section 1): every foreign key column
 * is indexed, and every table of the application schema has row level security switched on (except
 * the migration tool's own history table).
 */
class SchemaHealthIT extends PostgresIntegrationTest {

  @Test
  void everyForeignKeyColumnHasAnIndex() throws Exception {
    String sql =
        """
        select c.conrelid::regclass::text || '.' || a.attname
        from pg_constraint c
        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
        where c.contype = 'f' and c.connamespace = 'app'::regnamespace
          and not exists (select 1 from pg_index i where i.indrelid = c.conrelid and i.indkey[0] = c.conkey[1])
        order by 1
        """;
    assertThat(names(sql)).as("foreign key columns without an index").isEmpty();
  }

  @Test
  void everyTableHasRowLevelSecurityExceptTheMigrationHistory() throws Exception {
    String sql =
        """
        select c.relname from pg_class c
        where c.relnamespace = 'app'::regnamespace and c.relkind = 'r'
          and not c.relrowsecurity and c.relname <> 'flyway_schema_history'
        order by 1
        """;
    assertThat(names(sql)).as("tables without row level security").isEmpty();
  }

  private static List<String> names(String sql) throws Exception {
    List<String> found = new ArrayList<>();
    try (Connection connection = connectionAs(MIGRATOR_USER, MIGRATOR_PASSWORD);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        found.add(rows.getString(1));
      }
    }
    return found;
  }
}
