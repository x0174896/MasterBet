package it.masterbet.betting.domain;

import it.masterbet.football.domain.Score;
import it.masterbet.odds.domain.QuotedOutcome;
import org.springframework.stereotype.Component;

@Component
public class MarketSettlement {
  public boolean won(QuotedOutcome q, Score s) {
    if (!q.fixtureId().equals(s.fixtureId()))
      throw new IllegalArgumentException("Risultato della partita errata");
    int h, a;
    switch (q.market().period()) {
      case "p1" -> {
        h = s.homeHt();
        a = s.awayHt();
      }
      case "p2" -> {
        h = s.homeSecondHalf();
        a = s.awaySecondHalf();
      }
      case "fulltime" -> {
        h = s.homeFt();
        a = s.awayFt();
      }
      default -> throw new IllegalArgumentException("Periodo non liquidabile");
    }
    String outcome = q.outcome();
    return switch (q.market().type()) {
      case "1x2" -> {
        if (!outcome.matches("[1X2]")) throw new IllegalArgumentException("Esito non valido");
        yield outcome.equals(result(h, a));
      }
      case "bothteamsscore" -> {
        if (!outcome.equals("Yes") && !outcome.equals("No"))
          throw new IllegalArgumentException("Esito non valido");
        yield (h > 0 && a > 0) == outcome.equals("Yes");
      }
      case "totals", "teamtotals-team1", "teamtotals-team2" -> {
        if (q.market().line() == null) throw new IllegalArgumentException("Linea assente");
        double line = q.market().line().doubleValue();
        if (line < 0
            || Math.abs(line - Math.floor(line) - .5) > 1e-9
            || (!outcome.equals("Over") && !outcome.equals("Under")))
          throw new IllegalArgumentException("Linea con rimborso/non supportata");
        int total =
            q.market().type().equals("totals")
                ? h + a
                : q.market().type().endsWith("team1") ? h : a;
        yield outcome.equals("Over") ? total > line : total < line;
      }
      case "correctscore" -> {
        if (!outcome.matches("\\d+[-:]\\d+"))
          throw new IllegalArgumentException("Esito non valido");
        yield outcome.replace(':', '-').equals(h + "-" + a);
      }
      case "halftime-fulltime" -> {
        if (!q.market().period().equals("fulltime") || !outcome.matches("[1X2]/[1X2]"))
          throw new IllegalArgumentException("Esito non valido");
        yield outcome.equals(result(s.homeHt(), s.awayHt()) + "/" + result(s.homeFt(), s.awayFt()));
      }
      default -> throw new IllegalArgumentException("Mercato non liquidabile");
    };
  }

  private String result(int h, int a) {
    return h > a ? "1" : h == a ? "X" : "2";
  }
}
