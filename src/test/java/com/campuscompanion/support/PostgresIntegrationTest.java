package com.campuscompanion.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests. It starts one real PostgreSQL container for the whole test run
 * and prepares it the way the owner prepares Supabase: the roles cc_migrator and cc_app and the
 * schema app are created first (init-roles.sql), then Flyway migrates as cc_migrator and the
 * application connects as cc_app. H2 is never used.
 */
// Every test comes from one address, so the per-IP login limit is lifted here. LoginRateLimitIT
// lowers it again to test it.
@SpringBootTest
@TestPropertySource(properties = "app.security.login.max-attempts-per-ip=100000")
public abstract class PostgresIntegrationTest {

  protected static final String APP_USER = "cc_app";
  protected static final String APP_PASSWORD = "test-app-password";
  protected static final String MIGRATOR_USER = "cc_migrator";
  protected static final String MIGRATOR_PASSWORD = "test-migrator-password";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine").withInitScript("db/init-roles.sql");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USERNAME", () -> APP_USER);
    registry.add("DB_PASSWORD", () -> APP_PASSWORD);
    registry.add("DB_MIGRATION_USERNAME", () -> MIGRATOR_USER);
    registry.add("DB_MIGRATION_PASSWORD", () -> MIGRATOR_PASSWORD);
    registry.add("STUDENT_EMAIL_DOMAIN", () -> "university.example");
    // Tests build their own data; the demo seeder stays off whatever the local .env says.
    registry.add("DEMO_SEED_ENABLED", () -> "false");
  }

  /** A connection as the container's administrator, for inspecting what other roles may not see. */
  protected static Connection adminConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  protected static Connection connectionAs(String user, String password) throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), user, password);
  }
}
