package it.masterbet.model.application;

import it.masterbet.football.domain.*;
import it.masterbet.model.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ModelTrainingService {
  private final FeatureBuilder features;
  private final PoissonTrainer trainer;

  public ModelTrainingService(FeatureBuilder features, PoissonTrainer trainer) {
    this.features = features;
    this.trainer = trainer;
  }

  public ModelReport train(List<PlayedMatch> historic, List<Fixture> calendar) {
    var sorted =
        historic.stream().sorted(Comparator.comparing(m -> m.fixture().kickoff())).toList();
    if (sorted.size() < 60)
      throw new IllegalArgumentException("Storico insufficiente: almeno 60 partite richieste");
    int split = (int) Math.floor(sorted.size() * 0.8);
    // Evita che partite contemporanee finiscano sui due lati della separazione.
    while (split > 20
        && !sorted
            .get(split - 1)
            .fixture()
            .endedAt()
            .isBefore(sorted.get(split).fixture().kickoff())) split--;
    var training = sorted.subList(0, split);
    var validation = sorted.subList(split, sorted.size());
    double[][] x = new double[split][], xc = new double[split][], y = new double[split][];
    for (int i = 0; i < split; i++) {
      var m = training.get(i);
      var f = m.fixture();
      x[i] = features.build(f, f.kickoff(), historic, calendar, false);
      xc[i] = features.build(f, f.kickoff(), historic, calendar, true);
      y[i] = targets(m.score());
    }
    PoissonModel league = trainer.train(x, y), cups = trainer.train(xc, y);
    double baselineScore = 0, leagueScore = 0, cupsScore = 0;
    for (var m : validation) {
      var f = m.fixture();
      baselineScore += brier(features.baseline(f, f.kickoff(), historic), m.score());
      leagueScore +=
          brier(
              league.predict(features.build(f, f.kickoff(), historic, calendar, false)), m.score());
      cupsScore +=
          brier(cups.predict(features.build(f, f.kickoff(), historic, calendar, true)), m.score());
    }
    return new ModelReport(
        "norway-2025-poisson-v1",
        "Regressione Poisson con penalizzazione L2 (4 intensita: casa/ospite nei due tempi)",
        split,
        validation.size(),
        training.getFirst().fixture().kickoff(),
        training.getLast().fixture().endedAt(),
        validation.getFirst().fixture().kickoff(),
        validation.getLast().fixture().endedAt(),
        FeatureBuilder.NAMES,
        baselineScore / validation.size(),
        leagueScore / validation.size(),
        cupsScore / validation.size(),
        league,
        cups,
        "Prototipo su campione cronologico ridotto; Brier 1X2 (somma delle tre componenti), minore"
            + " e migliore. Gol indipendenti, dati di rosa e viaggio assenti. Nessuna efficacia"
            + " economica dimostrata. Supplementari visibili nei dati disponibili ma non usati come"
            + " feature per copertura incompleta.");
  }

  private double[] targets(Score s) {
    return new double[] {s.homeHt(), s.awayHt(), s.homeSecondHalf(), s.awaySecondHalf()};
  }

  private double brier(double[] rates, Score s) {
    var p = new GoalProbabilities(rates).fullTimeResult();
    int actual = s.homeFt() > s.awayFt() ? 0 : s.homeFt() == s.awayFt() ? 1 : 2;
    double sum = 0;
    for (int i = 0; i < 3; i++) sum += Math.pow(p[i] - (i == actual ? 1 : 0), 2);
    return sum;
  }
}
