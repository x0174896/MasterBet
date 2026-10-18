package it.masterbet.football.infrastructure;

import it.masterbet.football.domain.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NffScoreDownloader {
  private final NffArchive archive;

  public NffScoreDownloader(NffArchive archive) {
    this.archive = archive;
  }

  public Score download(Fixture fixture, NffSchedule.Match match, Instant cutoff) {
    if (!fixture.finishedBefore(cutoff))
      throw new IllegalArgumentException("Referto futuro vietato");
    return parse(fixture, match, archive.get("/fotballdata/kamp/?fiksId=" + match.nffId()));
  }

  public Score parse(Fixture fixture, NffSchedule.Match match, String html) {
    var title = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]+)\"").matcher(html);
    String expected =
        match.home()
            + " - "
            + match.away()
            + " - "
            + DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.of("Europe/Oslo"))
                .format(fixture.kickoff());
    if (!title.find()
        || !NffSchedule.text(title.group(1)).equals(expected)
        || !html.contains("/fotballdata/kamp/?fiksId=" + match.nffId() + "\""))
      throw new IllegalArgumentException("Identita referto NFF discordante");
    // Solo la prima scheda della partita, mai i risultati degli incontri correlati a fondo pagina.
    var card =
        Pattern.compile(
                "<div class=\"a_matchCard\">(.*?)<div class=\"cardFooter\">", Pattern.DOTALL)
            .matcher(html);
    if (!card.find()) throw new IllegalArgumentException("Scheda NFF assente");
    int[] ft = score(card.group(1), "endResult", false),
        ht = score(card.group(1), "halfTime", true);
    return new Score(fixture.id(), ht[0], ht[1], ft[0], ft[1], false);
  }

  private int[] score(String card, String name, boolean brackets) {
    String value = brackets ? "\\((\\d+)\\s*-\\s*(\\d+)\\)" : "(\\d+)\\s*-\\s*(\\d+)";
    var m =
        Pattern.compile("<div class=\"" + name + "\">\\s*" + value + "\\s*</div>").matcher(card);
    if (!m.find()) throw new IllegalArgumentException("Punteggio NFF mancante: " + name);
    return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
  }
}
