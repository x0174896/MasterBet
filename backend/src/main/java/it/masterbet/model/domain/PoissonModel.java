package it.masterbet.model.domain;

public record PoissonModel(double[] means, double[] scales, double[][] weights) {
  public double[] predict(double[] features) {
    if (features.length != means.length)
      throw new IllegalArgumentException("Feature incompatibili con il modello");
    double[] result = new double[4];
    for (int k = 0; k < 4; k++) {
      double eta = weights[k][0];
      for (int j = 0; j < features.length; j++)
        eta += weights[k][j + 1] * (features[j] - means[j]) / scales[j];
      result[k] = Math.max(0.05, Math.min(6, Math.exp(eta)));
    }
    return result;
  }
}
