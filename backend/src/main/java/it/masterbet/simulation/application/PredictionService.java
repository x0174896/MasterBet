package it.masterbet.simulation.application;

import it.masterbet.football.domain.PlayedMatch;
import it.masterbet.model.domain.*;
import it.masterbet.simulation.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PredictionService {
  private final FeatureBuilder features;

  public PredictionService(FeatureBuilder features) {
    this.features = features;
  }

  public List<Prediction> predict(SimulationDataset data, ModelReport report) {
    List<PlayedMatch> past = new ArrayList<>(data.trainingHistory());
    past.addAll(data.leagueHistory());
    List<Prediction> predictions = new ArrayList<>();
    for (var f : data.targets()) {
      var model =
          new GoalProbabilities(
              report
                  .cupsModel()
                  .predict(features.build(f, data.cutoff(), past, data.calendar(), true)));
      var baseline = new GoalProbabilities(features.baseline(f, data.cutoff(), past));
      for (var q : data.quotes())
        if (q.fixtureId().equals(f.id())) {
          var p = model.probability(q);
          var b = baseline.probability(q);
          if (p.isEmpty() || p.getAsDouble() <= 0)
            predictions.add(
                new Prediction(
                    q,
                    null,
                    null,
                    null,
                    null,
                    false,
                    "Mercato visibile; modello non disponibile per questo esito/regolamento"));
          else {
            double probability = p.getAsDouble(), value = q.price().doubleValue() * probability - 1;
            predictions.add(
                new Prediction(
                    q,
                    probability,
                    b.orElseThrow(),
                    1 / probability,
                    value,
                    value >= 0.05,
                    "Poisson con calendario coppe; soglia indicativa EV +5%"));
          }
        }
    }
    return List.copyOf(predictions);
  }
}
