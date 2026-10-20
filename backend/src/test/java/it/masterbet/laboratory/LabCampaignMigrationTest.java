package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LabCampaignMigrationTest {
  @Test
  void migrationFreezesExistingResultsWithoutChangingOriginalRows() {
    var dataSource =
        new DriverManagerDataSource(
            "jdbc:h2:mem:freeze-upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure()
        .dataSource(dataSource)
        .target("10")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO lab_run"
            + " VALUES('old','COMPLETED','2026-01-01T00:00:00Z',NULL,NULL,'hash','{}','unchanged')");
    jdbc.update(
        "INSERT INTO lab_match"
            + " VALUES('fixture',1,'2026','2026-01-01T00:00:00Z',FALSE,TRUE,'original score')");
    jdbc.update(
        "INSERT INTO lab_bet"
            + " VALUES('bet','old','fixture',1,FALSE,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','original"
            + " bet')");
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    assertEquals(
        "original score", jdbc.queryForObject("SELECT payload FROM lab_run_result", String.class));
    assertEquals("original bet", jdbc.queryForObject("SELECT payload FROM lab_bet", String.class));
    assertEquals("unchanged", jdbc.queryForObject("SELECT report FROM lab_run", String.class));
    jdbc.update("UPDATE lab_match SET payload='corrected score'");
    assertEquals(
        "original score", jdbc.queryForObject("SELECT payload FROM lab_run_result", String.class));
  }
}
