package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.LabSignalAudit;
import it.masterbet.model.domain.PoissonModel;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabSignalAuditTest {
  @Test
  void suppressesOnlyAGroupWhoseRemovalImprovesBothValidationBlocks() {
    var scales = new double[32];
    Arrays.fill(scales, 1);
    var weights = new double[4][33];
    weights[0][27] = 1;
    var model = new PoissonModel(new double[32], scales, weights);
    List<double[]> x = new ArrayList<>(), y = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      var row = new double[32];
      row[26] = 1;
      x.add(row);
      y.add(new double[] {1, 1, 1, 1});
    }
    var audit = new LabSignalAudit();
    var checks = audit.evaluate(model, x, y);
    assertEquals(List.of(26, 27, 28, 29, 30, 31), LabSignalAudit.disabled(checks));
    assertEquals(1, x.getFirst()[26]);
    assertEquals(0, LabSignalAudit.mask(x.getFirst(), LabSignalAudit.disabled(checks))[26]);
    for (int i = 40; i < 80; i++) y.set(i, new double[] {3, 1, 1, 1});
    assertTrue(LabSignalAudit.disabled(audit.evaluate(model, x, y)).isEmpty());
    assertTrue(audit.evaluate(model, x.subList(0, 20), y.subList(0, 20)).isEmpty());
  }
}
