package it.masterbet.laboratory.domain;

import it.masterbet.model.domain.PoissonModel;
import java.util.*;

/** Suppression checks on an already held-out chronological suffix, never on target results. */
public final class LabSignalAudit {
  public record Check(
      String group,
      List<Integer> columns,
      int observations,
      double firstGain,
      double secondGain,
      boolean disabled) {}

  public List<Check> evaluate(PoissonModel audit, List<double[]> x, List<double[]> y) {
    if (x.size() < 40) return List.of();
    Map<String, List<Integer>> groups = new LinkedHashMap<>();
    groups.put("Medie recenti e interazioni attacco/difesa", columns(0, 8, 12, 20));
    groups.put("Riposo, frequenza e trasferte", columns(20, 26));
    groups.put("Condizioni dell'ultima partita", columns(26, 32));
    if (audit.means().length == 42) groups.put("Statistiche dei gol di coppa", columns(32, 42));
    List<Check> checks = new ArrayList<>();
    int middle = x.size() / 2;
    for (var entry : groups.entrySet()) {
      double first = gain(audit, x, y, 0, middle, entry.getValue());
      double second = gain(audit, x, y, middle, x.size(), entry.getValue());
      checks.add(new Check(entry.getKey(), entry.getValue(), x.size(), first, second, false));
    }
    // At most one group: independently helpful suppressions need not be helpful together.
    var selected =
        checks.stream()
            .filter(c -> c.firstGain() >= .01 && c.secondGain() >= .01)
            .max(Comparator.comparingDouble(c -> Math.min(c.firstGain(), c.secondGain())));
    return checks.stream()
        .map(
            c ->
                new Check(
                    c.group(),
                    c.columns(),
                    c.observations(),
                    c.firstGain(),
                    c.secondGain(),
                    selected.isPresent() && selected.get().group().equals(c.group())))
        .toList();
  }

  public static List<Integer> disabled(List<Check> checks) {
    return checks.stream().filter(Check::disabled).flatMap(c -> c.columns().stream()).toList();
  }

  public static double[] mask(double[] input, List<Integer> columns) {
    var copy = input.clone();
    columns.forEach(i -> copy[i] = 0);
    return copy;
  }

  private double gain(
      PoissonModel audit,
      List<double[]> x,
      List<double[]> y,
      int start,
      int end,
      List<Integer> columns) {
    double original = 0, suppressed = 0;
    for (int i = start; i < end; i++) {
      var neutral = x.get(i).clone();
      columns.forEach(c -> neutral[c] = audit.means()[c]);
      original += deviance(audit.predict(x.get(i)), y.get(i));
      suppressed += deviance(audit.predict(neutral), y.get(i));
    }
    return (original - suppressed) / Math.max(1e-9, original);
  }

  private double deviance(double[] prediction, double[] truth) {
    double sum = 0;
    for (int k = 0; k < 4; k++) {
      double actual = truth[k], predicted = prediction[k];
      sum += 2 * (predicted - actual + (actual == 0 ? 0 : actual * Math.log(actual / predicted)));
    }
    return sum;
  }

  private static List<Integer> columns(int... bounds) {
    List<Integer> result = new ArrayList<>();
    for (int k = 0; k < bounds.length; k += 2)
      for (int i = bounds[k]; i < bounds[k + 1]; i++) result.add(i);
    return List.copyOf(result);
  }
}
