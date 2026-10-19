package it.masterbet.odds.domain;

/** The source gives incompatible prices or states at the same eligible instant. */
public final class AmbiguousPriceUpdateException extends IllegalArgumentException {
  public AmbiguousPriceUpdateException() {
    super("Aggiornamenti discordanti allo stesso timestamp");
  }
}
