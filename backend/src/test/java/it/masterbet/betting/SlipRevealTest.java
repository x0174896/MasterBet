package it.masterbet.betting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.masterbet.betting.application.*;
import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.RevealRepository;
import it.masterbet.football.domain.Score;
import it.masterbet.football.infrastructure.*;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class SlipRevealTest {
  @Test
  void revealUsesExactPeriodsAndSeparateStorageWithoutChangingSnapshot() {
    var helper = new BettingTest();
    var source = helper.snapshot(List.of(helper.prediction("f")));
    var slips =
        new SlipSelector(new ProfitTargetStaking())
            .select(source, source.predictions(), BettingConfig.defaults(), new BigDecimal("500"));
    var round =
        new BettingRound(
            source.id(),
            source.datasetSha256(),
            "model",
            helper.cutoff,
            helper.cutoff,
            BettingConfig.defaults(),
            new BigDecimal("500"),
            new BigDecimal("500"),
            slips,
            slips);
    var workspace = mock(BettingWorkspaceService.class);
    var repo = mock(RevealRepository.class);
    var snapshots = mock(SnapshotRepository.class);
    var calendar = mock(NffSchedule.class);
    var scores = mock(NffScoreDownloader.class);
    when(workspace.claimReveal("step", "cups", "S1")).thenReturn(round);
    when(repo.find("step", "cups", "S1")).thenReturn(Optional.empty());
    when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
    when(snapshots.find("step")).thenReturn(Optional.of(source));
    var target = source.dataset().targets().getFirst();
    var match = new NffSchedule.Match("123", 8, target.kickoff(), "A", "B");
    when(calendar.load()).thenReturn(List.of(match));
    when(calendar.identify(eq(target), any())).thenReturn(match);
    when(scores.download(any(), eq(match), any())).thenReturn(new Score("f", 1, 0, 1, 2, false));
    var service =
        new SlipRevealService(workspace, repo, snapshots, calendar, scores, new MarketSettlement());
    var reveal = service.reveal("step", "cups", "S1");
    assertEquals("WON", reveal.status());
    assertEquals(new BigDecimal("10.00"), reveal.payout());
    assertEquals(new BigDecimal("5.00"), reveal.profit());
    assertEquals(0, reveal.results().getFirst().score().homeSecondHalf());
    assertEquals(2, reveal.results().getFirst().score().awaySecondHalf());
    assertTrue(source.dataset().leagueHistory().isEmpty());
    assertNull(target.endedAt());
    verify(snapshots, never()).save(any());
    var order = inOrder(workspace, scores, repo);
    order.verify(workspace).claimReveal("step", "cups", "S1");
    order.verify(scores).download(any(), eq(match), any());
    order.verify(repo).save(any());
    when(repo.find("step", "cups", "S1")).thenReturn(Optional.of(reveal));
    assertSame(reveal, service.reveal("step", "cups", "S1"));
    verify(scores, times(1)).download(any(), any(), any());
  }
}
