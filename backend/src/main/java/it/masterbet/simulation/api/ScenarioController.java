package it.masterbet.simulation.api;

import it.masterbet.simulation.application.ScenarioQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ScenarioController {
  private final ScenarioQueryService service;

  public ScenarioController(ScenarioQueryService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "application", "MasterBet");
  }

  @GetMapping("/scenario")
  public ScenarioResponse scenario() {
    return service.scenario();
  }

  @GetMapping("/matches/{fixtureId}/quotes")
  public List<QuoteResponse> quotes(@PathVariable String fixtureId) {
    return service.quotes(fixtureId);
  }
}
