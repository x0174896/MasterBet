package it.masterbet.betting.api;

import it.masterbet.betting.application.*;
import it.masterbet.betting.domain.SlipReveal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/betting")
public class BettingController {
  private final BettingWorkspaceService workspace;
  private final SlipRevealService reveals;

  public BettingController(BettingWorkspaceService workspace, SlipRevealService reveals) {
    this.workspace = workspace;
    this.reveals = reveals;
  }

  public record SaveRequest(String expectedScenarioId) {}

  @PostMapping("/save")
  public BettingService.View save(@RequestBody SaveRequest request) {
    if (request.expectedScenarioId() == null)
      throw new IllegalArgumentException("Scenario richiesto");
    return workspace.save(request.expectedScenarioId());
  }

  @PostMapping("/{scenarioId}/{variant}/{slipId}/reveal")
  public SlipReveal reveal(
      @PathVariable String scenarioId, @PathVariable String variant, @PathVariable String slipId) {
    return reveals.reveal(scenarioId, variant, slipId);
  }
}
