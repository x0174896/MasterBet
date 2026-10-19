package it.masterbet.betting.application;

import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class RepriceBetsCommand implements ApplicationRunner {
  private final BettingRepricingService service;
  private final ConfigurableApplicationContext context;

  public RepriceBetsCommand(
      BettingRepricingService service, ConfigurableApplicationContext context) {
    this.service = service;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    if (args.containsOption("reprice-bets")) {
      if (args.containsOption("import-scenario"))
        throw new IllegalArgumentException("Importazione e ricalcolo sono comandi separati");
      System.out.println("MASTERBET_REPRICE_OK rounds=" + service.reprice());
      context.close();
    }
  }
}
