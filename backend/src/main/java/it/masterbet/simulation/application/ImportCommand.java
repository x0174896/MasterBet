package it.masterbet.simulation.application;

import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ImportCommand implements ApplicationRunner {
  private final ScenarioImportService importer;
  private final ConfigurableApplicationContext context;

  public ImportCommand(ScenarioImportService importer, ConfigurableApplicationContext context) {
    this.importer = importer;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    if (args.containsOption("import-scenario")) {
      if (args.containsOption("reprice-bets"))
        throw new IllegalArgumentException("Importazione e ricalcolo sono comandi separati");
      var snapshot = importer.importScenario();
      System.out.println(
          "MASTERBET_IMPORT_OK history="
              + snapshot.dataset().leagueHistory().size()
              + " training="
              + snapshot.dataset().trainingHistory().size()
              + " quotes="
              + snapshot.predictions().size()
              + " sha256="
              + snapshot.datasetSha256());
      context.close();
    }
  }
}
