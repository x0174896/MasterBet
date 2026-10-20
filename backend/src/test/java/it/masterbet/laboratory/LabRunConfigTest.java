package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.laboratory.domain.LabRunConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabRunConfigTest {
  @Test
  void oldConfigurationsRemainReadableAndNewStrategiesRequireAReference() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var legacy =
        mapper.readValue(
            """
            {"from":"2026-01-01","to":"2026-02-01","competitionIds":[384],"unitProfit":5,"mode":"HISTORICAL"}
            """,
            LabRunConfig.class);
    assertEquals("BASELINE", legacy.strategy());
    assertEquals("", legacy.name());
    assertNull(legacy.referenceRunId());
    for (String strategy : List.of("CALIBRATED", "SIGNALS", "FILTERED", "ADAPTIVE")) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new LabRunConfig(
                  legacy.from(),
                  legacy.to(),
                  legacy.competitionIds(),
                  legacy.unitProfit(),
                  legacy.mode(),
                  "New",
                  strategy,
                  null));
      var experiment =
          new LabRunConfig(
              legacy.from(),
              legacy.to(),
              legacy.competitionIds(),
              legacy.unitProfit(),
              legacy.mode(),
              " New ",
              strategy,
              "original");
      assertEquals(
          experiment, mapper.readValue(mapper.writeValueAsString(experiment), LabRunConfig.class));
      assertEquals("New", experiment.name());
    }
  }

  @Test
  void invalidNamesAndUnknownStrategiesAreRejected() {
    var from = LocalDate.of(2026, 1, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LabRunConfig(
                from, from, List.of(), null, "HISTORICAL", "x".repeat(121), "BASELINE", null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LabRunConfig(
                from, from, List.of(), null, "HISTORICAL", "test", "UNKNOWN", "original"));
  }
}
