package it.masterbet.model.domain;

import it.masterbet.odds.domain.QuotedOutcome;
import java.util.OptionalDouble;

/**
 * Modello di gol Poisson indipendenti nei due tempi; nessuna quota asiatica con rimborso viene
 * semplificata a binaria.
 */
public class GoalProbabilities {
  private final double[] lambda;
  private double[][] halfFull;

  public GoalProbabilities(double[] lambda) {
    if (lambda.length != 4) throw new IllegalArgumentException("Quattro intensita richieste");
    for (double l : lambda)
      if (!Double.isFinite(l) || l <= 0 || l > 6)
        throw new IllegalArgumentException("Intensita non valida");
    this.lambda = lambda.clone();
  }

  public OptionalDouble probability(QuotedOutcome q) {
    var m = q.market();
    String type = m.type(), period = m.period(), outcome = q.outcome();
    double h, a;
    switch (period) {
      case "p1" -> {
        h = lambda[0];
        a = lambda[1];
      }
      case "p2" -> {
        h = lambda[2];
        a = lambda[3];
      }
      case "fulltime" -> {
        h = lambda[0] + lambda[2];
        a = lambda[1] + lambda[3];
      }
      default -> {
        return OptionalDouble.empty();
      }
    }
    double p;
    switch (type) {
      case "1x2" -> {
        int r = resultIndex(outcome);
        if (r < 0) return OptionalDouble.empty();
        p = result(h, a)[r];
      }
      case "bothteamsscore" -> {
        p = (1 - Math.exp(-h)) * (1 - Math.exp(-a));
        if (outcome.equals("No")) p = 1 - p;
        else if (!outcome.equals("Yes")) return OptionalDouble.empty();
      }
      case "totals", "teamtotals-team1", "teamtotals-team2" -> {
        double line = m.line().doubleValue();
        if (line < 0 || Math.abs(line - Math.floor(line) - 0.5) > 1e-9)
          return OptionalDouble.empty();
        double total = type.equals("totals") ? h + a : type.endsWith("team1") ? h : a;
        double under = 0;
        for (int i = 0; i <= Math.floor(line); i++) under += poisson(total, i);
        if (outcome.equals("Over")) p = 1 - under;
        else if (outcome.equals("Under")) p = under;
        else return OptionalDouble.empty();
      }
      case "halftime-fulltime" -> {
        String[] parts = outcome.split("/");
        if (parts.length != 2) return OptionalDouble.empty();
        int i = resultIndex(parts[0]), j = resultIndex(parts[1]);
        if (i < 0 || j < 0) return OptionalDouble.empty();
        if (halfFull == null) halfFull = halfFull();
        p = halfFull[i][j];
      }
      case "correctscore" -> {
        if (!outcome.matches("\\d+[-:]\\d+")) return OptionalDouble.empty();
        String[] parts = outcome.split("[-:]");
        p = poisson(h, Integer.parseInt(parts[0])) * poisson(a, Integer.parseInt(parts[1]));
      }
      default -> {
        return OptionalDouble.empty();
      }
    }
    if (!Double.isFinite(p) || p < -1e-10 || p > 1 + 1e-10)
      throw new IllegalStateException("Probabilita incoerente");
    return OptionalDouble.of(Math.max(0, Math.min(1, p)));
  }

  public double[] fullTimeResult() {
    return result(lambda[0] + lambda[2], lambda[1] + lambda[3]);
  }

  public static double poisson(double mean, int goals) {
    double p = Math.exp(-mean);
    for (int i = 1; i <= goals; i++) p *= mean / i;
    return p;
  }

  private double[] result(double home, double away) {
    double[] result = new double[3];
    for (int h = 0; h <= 64; h++)
      for (int a = 0; a <= 64; a++) result[index(h, a)] += poisson(home, h) * poisson(away, a);
    return result;
  }

  private double[][] halfFull() {
    double[][] pmf = new double[4][33];
    for (int k = 0; k < 4; k++) for (int n = 0; n <= 32; n++) pmf[k][n] = poisson(lambda[k], n);
    double[][] result = new double[3][3];
    for (int h1 = 0; h1 <= 32; h1++)
      for (int a1 = 0; a1 <= 32; a1++)
        for (int h2 = 0; h2 <= 32; h2++)
          for (int a2 = 0; a2 <= 32; a2++)
            result[index(h1, a1)][index(h1 + h2, a1 + a2)] +=
                pmf[0][h1] * pmf[1][a1] * pmf[2][h2] * pmf[3][a2];
    return result;
  }

  private int resultIndex(String value) {
    return switch (value) {
      case "1" -> 0;
      case "X" -> 1;
      case "2" -> 2;
      default -> -1;
    };
  }

  private int index(int h, int a) {
    return h > a ? 0 : h == a ? 1 : 2;
  }
}
