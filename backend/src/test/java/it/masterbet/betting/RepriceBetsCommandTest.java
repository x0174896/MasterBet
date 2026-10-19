package it.masterbet.betting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.masterbet.betting.application.*;
import it.masterbet.simulation.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class RepriceBetsCommandTest {
  @Test
  void repricingRequiresExplicitFlagAndCannotRunTogetherWithImport() {
    var service = mock(BettingRepricingService.class);
    var importer = mock(ScenarioImportService.class);
    var context = mock(ConfigurableApplicationContext.class);
    var command = new RepriceBetsCommand(service, context);
    command.run(new DefaultApplicationArguments("--server.port=0"));
    verifyNoInteractions(service, context);
    var both = new DefaultApplicationArguments("--reprice-bets", "--import-scenario");
    assertThrows(IllegalArgumentException.class, () -> command.run(both));
    assertThrows(
        IllegalArgumentException.class, () -> new ImportCommand(importer, context).run(both));
    verifyNoInteractions(service, importer, context);
    command.run(new DefaultApplicationArguments("--reprice-bets"));
    verify(service).reprice();
    verify(context).close();
  }
}
