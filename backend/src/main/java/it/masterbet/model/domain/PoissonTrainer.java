package it.masterbet.model.domain;

import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class PoissonTrainer {
  private static final double RIDGE = 0.5;

  public PoissonModel train(double[][] x, double[][] y) {
    if (x.length < 20 || x.length != y.length)
      throw new IllegalArgumentException("Servono almeno 20 osservazioni di addestramento");
    int n = x.length, p = x[0].length;
    double[] means = new double[p], scales = new double[p];
    for (var row : x) for (int j = 0; j < p; j++) means[j] += row[j] / n;
    for (var row : x) for (int j = 0; j < p; j++) scales[j] += Math.pow(row[j] - means[j], 2) / n;
    for (int j = 0; j < p; j++) scales[j] = Math.max(1e-6, Math.sqrt(scales[j]));
    double[][] z = new double[n][p + 1];
    for (int i = 0; i < n; i++) {
      z[i][0] = 1;
      for (int j = 0; j < p; j++) z[i][j + 1] = (x[i][j] - means[j]) / scales[j];
    }
    double[][] weights = new double[4][p + 1];
    for (int k = 0; k < 4; k++) {
      double avg = 0;
      for (var row : y) avg += row[k] / n;
      double[] w = weights[k];
      w[0] = Math.log(Math.max(0.05, avg));
      for (int step = 0; step < 2000; step++) {
        double[] gradient = new double[p + 1];
        for (int i = 0; i < n; i++) {
          double error = Math.exp(dot(z[i], w)) - y[i][k];
          for (int j = 0; j <= p; j++) gradient[j] += error * z[i][j] / n;
        }
        for (int j = 1; j <= p; j++) gradient[j] += RIDGE * w[j];
        if (Arrays.stream(gradient).map(v -> v * v).sum() < 1e-12) break;
        double old = loss(z, y, k, w), rate = 0.1;
        boolean accepted = false;
        for (int attempt = 0; attempt < 30; attempt++) {
          double[] candidate = w.clone();
          for (int j = 0; j <= p; j++) candidate[j] -= rate * gradient[j];
          double next = loss(z, y, k, candidate);
          if (Double.isFinite(next) && next <= old) {
            w = candidate;
            accepted = true;
            break;
          }
          rate *= 0.5;
        }
        if (!accepted) break;
      }
      weights[k] = w;
    }
    return new PoissonModel(means, scales, weights);
  }

  private double loss(double[][] x, double[][] y, int target, double[] w) {
    double value = 0;
    for (int i = 0; i < x.length; i++) {
      double eta = dot(x[i], w);
      value += (Math.exp(eta) - y[i][target] * eta) / x.length;
    }
    for (int j = 1; j < w.length; j++) value += RIDGE * w[j] * w[j] / 2;
    return value;
  }

  private double dot(double[] a, double[] b) {
    double v = 0;
    for (int j = 0; j < a.length; j++) v += a[j] * b[j];
    return v;
  }
}
