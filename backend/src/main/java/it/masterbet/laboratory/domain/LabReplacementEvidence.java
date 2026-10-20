package it.masterbet.laboratory.domain;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Promotion evidence for replacements issued out of time, including proposals never played. */
public final class LabReplacementEvidence {
  public record Observation(
      String fixtureId,
      String family,
      Instant predictedAt,
      Instant settledAt,
      double stake,
      double net,
      double primaryNet) {}

  public record Check(
      String family,
      int support,
      int weeks,
      int firstSupport,
      int secondSupport,
      double firstRoi,
      double secondRoi,
      Double lowerRoi,
      Double lowerDifference,
      boolean allowed,
      String reason) {}

  public List<Check> evaluate(List<Observation> history, Instant cutoff) {
    Map<String, Observation> unique = new TreeMap<>();
    for (var o : history) {
      if (!o.settledAt().isBefore(cutoff) || !o.predictedAt().isBefore(o.settledAt())) continue;
      String key = o.family() + "|" + o.fixtureId();
      var existing = unique.putIfAbsent(key, o);
      if (existing != null && !existing.equals(o))
        throw new IllegalArgumentException(
            "Controlli alternativi discordanti per lo stesso incontro");
    }
    var groups =
        unique.values().stream()
            .collect(Collectors.groupingBy(Observation::family, TreeMap::new, Collectors.toList()));
    List<Check> checks = new ArrayList<>();
    for (var entry : groups.entrySet()) {
      var rows = entry.getValue();
      var weeks = rows.stream().map(Observation::predictedAt).distinct().sorted().toList();
      Instant middle = weeks.get(weeks.size() / 2);
      var first = rows.stream().filter(o -> o.predictedAt().isBefore(middle)).toList();
      var second = rows.stream().filter(o -> !o.predictedAt().isBefore(middle)).toList();
      double firstRoi = roi(first), secondRoi = roi(second);
      if (rows.size() < 30 || weeks.size() < 6 || first.size() < 10 || second.size() < 10) {
        checks.add(
            new Check(
                entry.getKey(),
                rows.size(),
                weeks.size(),
                first.size(),
                second.size(),
                firstRoi,
                secondRoi,
                null,
                null,
                false,
                "Servono 30 incontri, sei settimane e almeno dieci casi per blocco"));
        continue;
      }
      double turnover = rows.stream().mapToDouble(Observation::stake).sum(), ratio = roi(rows);
      double difference =
          rows.stream().mapToDouble(o -> o.net() - o.primaryNet()).average().orElse(0);
      var byWeek = rows.stream().collect(Collectors.groupingBy(Observation::predictedAt));
      double squaredRoi = 0, squaredDifference = 0;
      for (var week : byWeek.values()) {
        double residual = week.stream().mapToDouble(o -> o.net() - ratio * o.stake()).sum();
        double paired = week.stream().mapToDouble(o -> o.net() - o.primaryNet() - difference).sum();
        squaredRoi += residual * residual;
        squaredDifference += paired * paired;
      }
      double adjustment = byWeek.size() / (byWeek.size() - 1.);
      double lowerRoi = ratio - 2 * Math.sqrt(adjustment * squaredRoi) / turnover;
      double lowerDifference =
          difference - 2 * Math.sqrt(adjustment * squaredDifference) / rows.size();
      boolean allowed = firstRoi > 0 && secondRoi > 0 && lowerRoi > 0 && lowerDifference > 0;
      checks.add(
          new Check(
              entry.getKey(),
              rows.size(),
              weeks.size(),
              first.size(),
              second.size(),
              firstRoi,
              secondRoi,
              lowerRoi,
              lowerDifference,
              allowed,
              allowed
                  ? "Rendimento positivo nei due blocchi e vantaggio abbinato confermato nei"
                        + " controlli esplorativi"
                  : "Vantaggio della sostituzione non confermato rispetto alla prima scelta e"
                        + " all'astensione"));
    }
    return List.copyOf(checks);
  }

  private double roi(List<Observation> rows) {
    return rows.stream().mapToDouble(Observation::net).sum()
        / Math.max(.01, rows.stream().mapToDouble(Observation::stake).sum());
  }
}
