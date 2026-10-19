package it.masterbet.laboratory.domain;

public class LabProcessConflict extends IllegalStateException {
  public LabProcessConflict(String message) {
    super(message);
  }
}
