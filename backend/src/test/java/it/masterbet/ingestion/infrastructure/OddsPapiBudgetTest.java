package it.masterbet.ingestion.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OddsPapiBudgetTest {
  @Test
  void freeHistoricalOddsRemainUsableAfterPaidCallsReachTheReserve() {
    assertTrue(OddsPapiClient.hasBudget(3, 2, 1));
    assertTrue(OddsPapiClient.hasBudget(1, 0, 1));
    assertFalse(OddsPapiClient.hasBudget(1, 1, 1));
    assertFalse(OddsPapiClient.hasBudget(2, 2, 1));
    assertFalse(OddsPapiClient.hasBudget(0, 0, 0));
  }
}
