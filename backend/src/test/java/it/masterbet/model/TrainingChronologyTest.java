package it.masterbet.model;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.TestMatches;
import it.masterbet.football.domain.*;
import it.masterbet.model.application.ModelTrainingService;
import it.masterbet.model.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrainingChronologyTest {
  @Test
  void validationLabelsCannotChangeFittedWeights() {
    List<PlayedMatch> original = new ArrayList<>(), changed = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      String at = Instant.parse("2025-07-01T12:00:00Z").plusSeconds(i * 86400L).toString();
      original.add(TestMatches.played("m" + i, at, i % 3, i % 2));
      changed.add(TestMatches.played("m" + i, at, i >= 64 ? 9 : i % 3, i % 2));
    }
    var calendar = original.stream().map(PlayedMatch::fixture).toList();
    var service = new ModelTrainingService(new FeatureBuilder(), new PoissonTrainer());
    var a = service.train(original, calendar);
    var b = service.train(changed, calendar);
    assertTrue(a.trainingThrough().isBefore(a.validationFrom()));
    for (int k = 0; k < 4; k++)
      assertArrayEquals(a.cupsModel().weights()[k], b.cupsModel().weights()[k]);
    assertEquals(64, a.trainingMatches());
    assertEquals(16, a.validationMatches());
  }
}
