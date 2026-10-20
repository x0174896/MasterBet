package it.masterbet.laboratory.domain;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Conditional calibration uses forecasts of alternatives too; each fixture has unit fitting weight.
 */
public final class LabMarketLearning {
  public record Observation(LabLearning.Observation forecast, boolean primary) {}

  public record BandCheck(
      String band, int support, int selected, double predicted, double observed, boolean ready) {}

  public record Calibration(
      String family,
      int support,
      int fitted,
      int validation,
      Instant validationFrom,
      Instant latestFitResult,
      double offset,
      double slope,
      double minProbability,
      double maxProbability,
      Double rawBrier,
      Double calibratedBrier,
      boolean accepted,
      List<BandCheck> bands,
      String reason) {
    public double apply(double probability) {
      return accepted ? logistic(offset + slope * logit(probability)) : probability;
    }

    public boolean supports(double probability) {
      return probability >= minProbability - .025
          && probability <= maxProbability + .025
          && bands.stream()
              .anyMatch(b -> b.band().equals(LabMarketFrame.band(probability)) && b.ready());
    }
  }

  public record FamilyRules(String family, List<LabLearning.Rule> rules) {}

  public record PolicyCheck(String policy, int settled, double turnover, double net) {}

  public record Snapshot(
      String version,
      int fixtures,
      int candidates,
      int alternatives,
      Instant latestResult,
      Calibration global,
      List<Calibration> families,
      List<FamilyRules> conditions,
      List<PolicyCheck> policies,
      List<LabReplacementEvidence.Check> replacements) {
    public Snapshot(
        String version,
        int fixtures,
        int candidates,
        int alternatives,
        Instant latestResult,
        Calibration global,
        List<Calibration> families,
        List<FamilyRules> conditions,
        List<PolicyCheck> policies) {
      this(
          version,
          fixtures,
          candidates,
          alternatives,
          latestResult,
          global,
          families,
          conditions,
          policies,
          List.of());
    }

    public Snapshot {
      replacements = replacements == null ? List.of() : List.copyOf(replacements);
    }

    public Optional<Calibration> calibration(String family) {
      return families.stream().filter(c -> c.family().equals(family)).findFirst();
    }
  }

  public Snapshot fit(
      List<Observation> history, Instant cutoff, boolean filters, List<PolicyCheck> policies) {
    return fit(history, cutoff, filters, policies, List.of());
  }

  public Snapshot fit(
      List<Observation> history,
      Instant cutoff,
      boolean filters,
      List<PolicyCheck> policies,
      List<LabReplacementEvidence.Observation> replacements) {
    var past =
        history.stream()
            .filter(
                o ->
                    o.forecast().settledAt().isBefore(cutoff)
                        && o.forecast().predictedAt().isBefore(o.forecast().settledAt())
                        && o.forecast().predictedAt().isBefore(cutoff))
            .sorted(
                Comparator.comparing((Observation o) -> o.forecast().predictedAt())
                    .thenComparing(o -> o.forecast().fixtureId()))
            .toList();
    var cuts = past.stream().map(o -> o.forecast().predictedAt()).distinct().sorted().toList();
    Instant boundary =
        cuts.size() < 2
            ? null
            : cuts.get(Math.min(cuts.size() - 1, Math.max(1, (int) (cuts.size() * .7))));
    var global = calibrate("all", past, boundary, new double[] {0, 1});
    // The global prior is fitted on the same purged TRAIN prefix as each family, never its
    // validation.
    double[] prior = {global.offset(), global.slope()};
    var groups =
        past.stream()
            .collect(
                Collectors.groupingBy(
                    o -> LabMarketFrame.family(o.forecast().quote()),
                    TreeMap::new,
                    Collectors.toList()));
    List<Calibration> families = new ArrayList<>();
    List<FamilyRules> conditions = new ArrayList<>();
    var legacy = new LabLearning();
    for (var entry : groups.entrySet()) {
      families.add(calibrate(entry.getKey(), entry.getValue(), boundary, prior));
      if (filters) {
        // One representative per fixture/family keeps lines and bands from inflating rule support.
        Map<String, LabLearning.Observation> representative = new TreeMap<>();
        for (var item : entry.getValue()) {
          var o = item.forecast();
          representative.merge(
              o.fixtureId(),
              o,
              (a, b) ->
                  a.probability() * a.quote().price().doubleValue()
                          >= b.probability() * b.quote().price().doubleValue()
                      ? a
                      : b);
        }
        conditions.add(
            new FamilyRules(
                entry.getKey(), legacy.rules(new ArrayList<>(representative.values()))));
      }
    }
    return new Snapshot(
        "conditional-markets-v2",
        unique(past),
        past.size(),
        (int) past.stream().filter(o -> !o.primary()).count(),
        past.stream()
            .map(o -> o.forecast().settledAt())
            .max(Comparator.naturalOrder())
            .orElse(null),
        global,
        List.copyOf(families),
        List.copyOf(conditions),
        List.copyOf(policies),
        new LabReplacementEvidence().evaluate(replacements, cutoff));
  }

  private Calibration calibrate(
      String family, List<Observation> all, Instant boundary, double[] prior) {
    var train =
        boundary == null
            ? List.<Observation>of()
            : all.stream()
                .filter(
                    o ->
                        o.forecast().predictedAt().isBefore(boundary)
                            && o.forecast().settledAt().isBefore(boundary))
                .toList();
    var validation =
        boundary == null
            ? List.<Observation>of()
            : all.stream().filter(o -> !o.forecast().predictedAt().isBefore(boundary)).toList();
    Instant latest =
        train.stream()
            .map(o -> o.forecast().settledAt())
            .max(Comparator.naturalOrder())
            .orElse(null);
    if (unique(all) < 100 || unique(train) < 60 || unique(validation) < 30) {
      return new Calibration(
          family,
          unique(all),
          unique(train),
          unique(validation),
          boundary,
          latest,
          prior[0],
          prior[1],
          0,
          0,
          null,
          null,
          false,
          List.of(),
          "Campione cronologico insufficiente: 100/60/30 incontri richiesti");
    }
    double[] coefficients = fit(train, prior);
    double[] raw = loss(validation, 0, 1),
        corrected = loss(validation, coefficients[0], coefficients[1]);
    boolean accepted = corrected[0] + .001 < raw[0] && corrected[1] < raw[1];
    double offset = accepted ? coefficients[0] : 0, slope = accepted ? coefficients[1] : 1;
    List<BandCheck> checks = new ArrayList<>();
    for (String band : List.of("50-60", "60-70", "70-80", "80-100")) {
      var local =
          validation.stream()
              .filter(o -> LabMarketFrame.band(o.forecast().probability()).equals(band))
              .toList();
      var selected =
          local.stream()
              .filter(
                  o -> {
                    double p = logistic(offset + slope * logit(o.forecast().probability()));
                    return p >= .5 && p * o.forecast().quote().price().doubleValue() >= 1.05;
                  })
              .toList();
      double predicted =
          fixtureAverage(
              selected, o -> logistic(offset + slope * logit(o.forecast().probability())));
      double observed = fixtureAverage(selected, o -> o.forecast().won() ? 1 : 0);
      double se = residualError(selected, offset, slope);
      // Check the EV-selected subset as well as the complete candidate population.
      boolean ready =
          unique(local) >= 15 && unique(selected) >= 10 && predicted - observed <= .02 + 2 * se;
      checks.add(new BandCheck(band, unique(local), unique(selected), predicted, observed, ready));
    }
    return new Calibration(
        family,
        unique(all),
        unique(train),
        unique(validation),
        boundary,
        latest,
        coefficients[0],
        coefficients[1],
        train.stream().mapToDouble(o -> o.forecast().probability()).min().orElse(0),
        train.stream().mapToDouble(o -> o.forecast().probability()).max().orElse(0),
        raw[0],
        corrected[0],
        accepted,
        List.copyOf(checks),
        accepted
            ? "Correzione verificata su previsioni successive a tutti gli esiti di training"
            : "Correzione non confermata; probabilita grezze ammesse solo nelle fasce verificate");
  }

  private double[] fit(List<Observation> train, double[] prior) {
    Map<String, Long> counts = counts(train);
    double a = prior[0], b = prior[1];
    for (int iteration = 0; iteration < 40; iteration++) {
      double ga = 10 * (a - prior[0]), gb = 10 * (b - prior[1]), aa = 10, ab = 0, bb = 10;
      for (var item : train) {
        var o = item.forecast();
        double weight = 1. / counts.get(o.fixtureId()),
            x = logit(o.probability()),
            p = logistic(a + b * x);
        double residual = weight * (p - (o.won() ? 1 : 0)), curvature = weight * p * (1 - p);
        ga += residual;
        gb += residual * x;
        aa += curvature;
        ab += curvature * x;
        bb += curvature * x * x;
      }
      double determinant = aa * bb - ab * ab;
      double nextA = Math.max(-3, Math.min(3, a - (bb * ga - ab * gb) / determinant));
      double nextB = Math.max(0, Math.min(2, b - (aa * gb - ab * ga) / determinant));
      if (Math.abs(nextA - a) + Math.abs(nextB - b) < 1e-9) {
        a = nextA;
        b = nextB;
        break;
      }
      a = nextA;
      b = nextB;
    }
    return new double[] {a, b};
  }

  private double[] loss(List<Observation> items, double offset, double slope) {
    Map<String, Long> counts = counts(items);
    double brier = 0, log = 0, total = 0;
    for (var item : items) {
      var o = item.forecast();
      double weight = 1. / counts.get(o.fixtureId()),
          p = Math.max(.001, Math.min(.999, logistic(offset + slope * logit(o.probability()))));
      double y = o.won() ? 1 : 0;
      brier += weight * Math.pow(p - y, 2);
      log -= weight * (y * Math.log(p) + (1 - y) * Math.log(1 - p));
      total += weight;
    }
    return new double[] {brier / total, log / total};
  }

  private double residualError(List<Observation> selected, double offset, double slope) {
    Map<String, Double> residuals = new HashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    for (var item : selected) {
      var o = item.forecast();
      residuals.merge(
          o.fixtureId(),
          logistic(offset + slope * logit(o.probability())) - (o.won() ? 1 : 0),
          Double::sum);
      counts.merge(o.fixtureId(), 1, Integer::sum);
    }
    if (residuals.size() < 2) return 1;
    var values =
        residuals.entrySet().stream()
            .mapToDouble(e -> e.getValue() / counts.get(e.getKey()))
            .toArray();
    double mean = Arrays.stream(values).average().orElse(0);
    return Math.sqrt(
        Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).sum()
            / (values.length * (values.length - 1.)));
  }

  private double fixtureAverage(
      List<Observation> selected, java.util.function.ToDoubleFunction<Observation> value) {
    if (selected.isEmpty()) return 0;
    var counts = counts(selected);
    return selected.stream()
            .mapToDouble(o -> value.applyAsDouble(o) / counts.get(o.forecast().fixtureId()))
            .sum()
        / counts.size();
  }

  private static Map<String, Long> counts(List<Observation> observations) {
    return observations.stream()
        .collect(Collectors.groupingBy(o -> o.forecast().fixtureId(), Collectors.counting()));
  }

  private static int unique(List<Observation> observations) {
    return counts(observations).size();
  }

  private static double logit(double p) {
    p = Math.max(.001, Math.min(.999, p));
    return Math.log(p / (1 - p));
  }

  private static double logistic(double x) {
    return 1 / (1 + Math.exp(-x));
  }
}
