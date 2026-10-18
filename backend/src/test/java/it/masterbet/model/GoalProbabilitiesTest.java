package it.masterbet.model;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.model.domain.*;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalProbabilitiesTest {
  private final GoalProbabilities model = new GoalProbabilities(new double[] {0.8, 0.5, 1.1, 0.7});

  private QuotedOutcome q(String type, String period, String line, String outcome) {
    return new QuotedOutcome(
        "f",
        "b",
        new Market("m", "name", type, period, new BigDecimal(line), false, Map.of()),
        "o",
        outcome,
        new BigDecimal("2"),
        Instant.EPOCH);
  }

  @Test
  void fullTimeOutcomesSumToOne() {
    assertEquals(1, java.util.Arrays.stream(model.fullTimeResult()).sum(), 1e-10);
  }

  @Test
  void overUnderComplementAtAllPeriods() {
    for (String period : new String[] {"p1", "p2", "fulltime"})
      assertEquals(
          1,
          model.probability(q("totals", period, "1.5", "Over")).orElseThrow()
              + model.probability(q("totals", period, "1.5", "Under")).orElseThrow(),
          1e-12);
  }

  @Test
  void halfFullCombinationsSumToOne() {
    double sum = 0;
    for (String h : new String[] {"1", "X", "2"})
      for (String f : new String[] {"1", "X", "2"})
        sum +=
            model.probability(q("halftime-fulltime", "fulltime", "0", h + "/" + f)).orElseThrow();
    assertEquals(1, sum, 1e-10);
  }

  @Test
  void secondHalfDoesNotUseFullTimeTotal() {
    double p = model.probability(q("totals", "p2", "0.5", "Under")).orElseThrow();
    assertEquals(Math.exp(-1.8), p, 1e-12);
  }

  @Test
  void asianAndIntegerLinesAreNotTreatedAsBinary() {
    assertTrue(model.probability(q("totals", "fulltime", "2.25", "Over")).isEmpty());
    assertTrue(model.probability(q("totals", "fulltime", "2", "Over")).isEmpty());
  }

  @Test
  void unsupportedMarketsDoNotInventProbabilities() {
    assertTrue(model.probability(q("totals-corners", "fulltime", "8.5", "Over")).isEmpty());
  }

  @Test
  void invalidLambdasFail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GoalProbabilities(new double[] {1, Double.NaN, 1, 1}));
  }

  @Test
  void trainerLearnsConstantRatesDeterministically() {
    double[][] x = new double[60][2], y = new double[60][4];
    for (int i = 0; i < 60; i++) {
      x[i][0] = 1;
      x[i][1] = 3;
      y[i] = new double[] {1, 2, 1, 3};
    }
    var trainer = new PoissonTrainer();
    var learned = trainer.train(x, y);
    assertArrayEquals(new double[] {1, 2, 1, 3}, learned.predict(new double[] {1, 3}), 1e-6);
    assertArrayEquals(learned.weights()[0], trainer.train(x, y).weights()[0]);
  }
}
