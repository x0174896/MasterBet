package it.masterbet.betting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Una unità indica l'utile netto obiettivo, condizionato alla vincita della schedina. */
@Component
public class ProfitTargetStaking {
  public BigDecimal stake(BigDecimal targetProfit, BigDecimal price) {
    if (targetProfit == null
        || targetProfit.signum() <= 0
        || price == null
        || price.compareTo(BigDecimal.ONE) <= 0)
      throw new IllegalArgumentException("Utile positivo e quota maggiore di uno richiesti");
    return targetProfit
        .divide(price.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP)
        .max(new BigDecimal("0.01"));
  }

  public BettingRound.Slip reprice(BettingRound.Slip slip, BigDecimal targetProfit) {
    return new BettingRound.Slip(
        slip.id(),
        slip.kind(),
        slip.bookmaker(),
        slip.legs(),
        BigDecimal.ONE,
        stake(targetProfit, slip.price()),
        slip.price(),
        slip.probability(),
        slip.expectedReturn(),
        rationale(slip.kind()));
  }

  public String rationale(String kind) {
    return "EV >= 5%, probabilita stimata >= 50%, quota singola <= 5; una esposizione per partita. "
        + "Puntata per un utile netto obiettivo di una unita, arrotondata al centesimo. "
        + (kind.equals("SINGLE")
            ? "Nessun recupero delle perdite."
            : "Prodotto simulato delle quote; probabilita sotto ipotesi di indipendenza, multipla"
                  + " non verificata dal bookmaker.");
  }
}
