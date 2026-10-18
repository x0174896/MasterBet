package it.masterbet.simulation.api;

import it.masterbet.betting.application.BettingService;
import it.masterbet.betting.domain.BettingConfig;
import it.masterbet.simulation.application.*;
import it.masterbet.simulation.infrastructure.AdvanceJobRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdvanceController {
  private final AdvanceService advance;
  private final AdvanceJobRepository jobs;
  private final BettingService bets;
  private final ScenarioQueryService query;

  public AdvanceController(
      AdvanceService advance,
      AdvanceJobRepository jobs,
      BettingService bets,
      ScenarioQueryService query) {
    this.advance = advance;
    this.jobs = jobs;
    this.bets = bets;
    this.query = query;
  }

  public record Request(String expectedScenarioId, BettingConfig config) {}

  @GetMapping("/betting")
  public BettingService.View betting() {
    return bets.view(query.load());
  }

  @GetMapping("/advance-job")
  public AdvanceJobRepository.Status status() {
    return jobs.status();
  }

  @PostMapping("/advance-job")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public AdvanceJobRepository.Status start(@RequestBody Request request) {
    if (request.expectedScenarioId() == null || request.config() == null)
      throw new IllegalArgumentException("Scenario e configurazione richiesti");
    return advance.start(request.expectedScenarioId(), request.config());
  }
}
