package it.masterbet.football.infrastructure;

import it.masterbet.football.domain.Fixture;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class NffSchedule {
  public record Match(String nffId, int round, Instant kickoff, String home, String away) {}

  private final NffArchive archive;

  public NffSchedule(NffArchive archive) {
    this.archive = archive;
  }

  public List<Match> load() {
    return parse(archive.get("/fotballdata/turnering/hjem/?fiksId=206092&underside=kamper"));
  }

  public List<Match> parse(String html) {
    Map<String, Match> found = new LinkedHashMap<>();
    var rows = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(html);
    while (rows.find()) {
      var cells = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.DOTALL).matcher(rows.group(1));
      List<String> raw = new ArrayList<>();
      while (cells.find()) raw.add(cells.group(1));
      if (raw.size() < 9
          || !text(raw.get(0)).matches("\\d+")
          || !text(raw.get(1)).matches("\\d{2}\\.\\d{2}\\.2026")) continue;
      var id = Pattern.compile("fiksId=(\\d+)").matcher(raw.get(1));
      if (!id.find()) throw new IllegalArgumentException("Identita NFF assente");
      var kickoff =
          LocalDateTime.parse(
                  text(raw.get(1)) + " " + text(raw.get(3)),
                  DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
              .atZone(ZoneId.of("Europe/Oslo"))
              .toInstant();
      // Non leggere la cella del risultato: il calendario contiene soltanto identita e orari.
      var match =
          new Match(
              id.group(1),
              Integer.parseInt(text(raw.get(0))),
              kickoff,
              text(raw.get(4)),
              text(raw.get(6)));
      var previous = found.putIfAbsent(match.nffId(), match);
      if (previous != null && !previous.equals(match))
        throw new IllegalArgumentException("Calendario NFF ambiguo");
    }
    if (found.size() < 200) throw new IllegalArgumentException("Calendario ufficiale incompleto");
    return List.copyOf(found.values());
  }

  public Match identify(Fixture fixture, List<Match> schedule) {
    var matches =
        schedule.stream()
            .filter(
                m ->
                    m.home().equals(alias(fixture.home()))
                        && m.away().equals(alias(fixture.away()))
                        && m.kickoff().equals(fixture.kickoff()))
            .toList();
    if (matches.size() != 1)
      throw new IllegalArgumentException("Identita/orario discordante con NFF: " + fixture.id());
    return matches.getFirst();
  }

  public static String text(String html) {
    return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", "")).strip();
  }

  public static String alias(String name) {
    return switch (name) {
      case "Viking FK" -> "Viking";
      case "Molde FK" -> "Molde";
      case "Rosenborg BK" -> "Rosenborg";
      case "Kristiansund BK" -> "Kristiansund";
      case "SK Brann" -> "Brann";
      case "KFUM Oslo" -> "KFUM";
      case "IK Start" -> "Start";
      case "Vaalerenga IF" -> "Vålerenga";
      case "Aalesunds FK" -> "Aalesund";
      case "Lillestroem SK" -> "Lillestrøm";
      case "Tromsoe IL" -> "Tromsø";
      case "Fredrikstad FK" -> "Fredrikstad";
      case "Bodoe/Glimt" -> "Bodø/Glimt";
      default -> name;
    };
  }
}
