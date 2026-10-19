package it.masterbet.laboratory.domain;

import java.util.*;

/** Candidate grid decided before outcomes; final 20% is a chronological confirmation sample. */
public final class LabPatternMiner {
  public record Observation(String population, double[] x, double[] goals) {}

  public record Pattern(
      String population,
      String condition,
      String outcome,
      int support,
      int occurrences,
      double frequency,
      double base,
      double lift,
      int validationSupport,
      double validationFrequency,
      double validationBase,
      boolean confirmed) {}

  public List<Pattern> mine(List<Observation> data) {
    return data.stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                Observation::population, TreeMap::new, java.util.stream.Collectors.toList()))
        .values()
        .stream()
        .flatMap(rows -> minePopulation(rows).stream())
        .toList();
  }

  private List<Pattern> minePopulation(List<Observation> data) {
    int split = (int) (data.size() * .8);
    if (split < 20) return List.of();
    List<Pattern> found = new ArrayList<>();
    int conditions = data.getFirst().x().length > 32 ? 6 : 3;
    for (int side = 0; side < 2; side++)
      for (int condition = 0; condition < conditions; condition++)
        for (int outcome = 0; outcome < 4; outcome++) {
          int[] train = count(data.subList(0, split), side, condition, outcome),
              valid = count(data.subList(split, data.size()), side, condition, outcome);
          if (train[0] < 8 || train[2] == 0) continue;
          double frequency = (double) train[1] / train[0],
              base = (double) train[3] / train[2],
              lift = base == 0 ? 0 : frequency / base;
          double vf = valid[0] == 0 ? 0 : (double) valid[1] / valid[0],
              vb = valid[2] == 0 ? 0 : (double) valid[3] / valid[2];
          String who = side == 0 ? "Casa" : "Ospite";
          String name =
              who
                  + ": "
                  + List.of(
                          "precedente di campionato con almeno 3 gol subiti",
                          "precedente di campionato senza gol segnati",
                          "precedente di campionato senza gol nel primo tempo",
                          "coppe recenti senza gol medi nel primo tempo",
                          "coppe recenti con almeno 2 gol medi segnati",
                          "coppe recenti con almeno 3 gol medi subiti")
                      .get(condition);
          String target =
              who
                  + ": "
                  + List.of(
                          "non segna nel primo tempo",
                          "non segna nel secondo tempo",
                          "segna almeno 2 gol",
                          "non segna nell'intera partita")
                      .get(outcome);
          // Exploratory penalty for searching many conditions; this is not a calibrated p-value.
          double margin =
              valid[0] == 0 ? 1 : Math.sqrt(Math.log(conditions * 8 / .05) / (2 * valid[0]));
          found.add(
              new Pattern(
                  data.getFirst().population(),
                  name,
                  target,
                  train[0],
                  train[1],
                  frequency,
                  base,
                  lift,
                  valid[0],
                  vf,
                  vb,
                  train[0] >= 15 && valid[0] >= 10 && lift >= 1.15 && vf - vb > margin));
        }
    return found.stream()
        .sorted(
            Comparator.comparing(Pattern::confirmed)
                .reversed()
                .thenComparing(Comparator.comparingDouble(Pattern::lift).reversed()))
        .limit(4)
        .toList();
  }

  private int[] count(List<Observation> data, int side, int condition, int outcome) {
    int[] r = new int[4];
    for (var row : data) {
      double ht = row.goals()[side], sh = row.goals()[side + 2];
      boolean success =
          switch (outcome) {
            case 0 -> ht == 0;
            case 1 -> sh == 0;
            case 2 -> ht + sh >= 2;
            default -> ht + sh == 0;
          };
      r[2]++;
      if (success) r[3]++;
      boolean match;
      if (condition < 3) match = row.x()[26 + side * 3 + condition] > 0;
      else {
        int k = 32 + side * 5;
        match =
            row.x().length > k + 4
                && row.x()[k + 4] > 0
                && switch (condition) {
                  case 3 -> row.x()[k] == 0;
                  case 4 -> row.x()[k] + row.x()[k + 2] >= 2;
                  default -> row.x()[k + 1] + row.x()[k + 3] >= 3;
                };
      }
      if (match) {
        r[0]++;
        if (success) r[1]++;
      }
    }
    return r;
  }
}
