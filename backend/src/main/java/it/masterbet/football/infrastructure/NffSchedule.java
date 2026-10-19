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

  private static final ZoneId NORWAY = ZoneId.of("Europe/Oslo");
  public static final Duration MAX_TIME_DIFFERENCE = Duration.ofHours(2);
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
              .atZone(NORWAY)
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
    var matches = schedule.stream().filter(m -> sameEvent(fixture, m)).toList();
    if (matches.size() != 1)
      throw new IllegalArgumentException(
          "Identita NFF non univoca per "
              + fixture.id()
              + " ("
              + fixture.home()
              + " - "
              + fixture.away()
              + ", "
              + fixture.kickoff()
              + "): "
              + matches.size()
              + " corrispondenze nella stessa data, tolleranza oraria 2 ore");
    return matches.getFirst();
  }

  private boolean sameEvent(Fixture fixture, Match match) {
    return fixture.tournamentId() == 20
        && Objects.equals(fixture.seasonId(), 138092L)
        && match.home().equals(alias(fixture.home()))
        && match.away().equals(alias(fixture.away()))
        && match
            .kickoff()
            .atZone(NORWAY)
            .toLocalDate()
            .equals(fixture.kickoff().atZone(NORWAY).toLocalDate())
        && Duration.between(match.kickoff(), fixture.kickoff()).abs().compareTo(MAX_TIME_DIFFERENCE)
            <= 0;
  }

  /** Normalizza solo la copia usata dal nuovo scenario; l'archivio del provider resta immutato. */
  public Fixture reconcile(Fixture fixture, List<Match> schedule) {
    var match = identify(fixture, schedule);
    if (match.kickoff().equals(fixture.kickoff())) return fixture;
    Instant endedAt = fixture.endedAt();
    String basis = "NFF_SCHEDULE";
    if (endedAt != null) {
      if ("ACTUAL_END".equals(fixture.timingBasis()) && endedAt.isAfter(match.kickoff())) {
        basis = "ACTUAL_END";
      } else {
        // Il limite prudenziale segue l'orario ufficiale, mai il risultato letto nel calendario.
        endedAt = match.kickoff().plus(Duration.ofDays(1));
        basis = "NFF_CONSERVATIVE_24H_BOUND";
      }
    }
    org.slf4j.LoggerFactory.getLogger(NffSchedule.class)
        .info(
            "Orario riconciliato {} / NFF {}: provider {}, ufficiale {}",
            fixture.id(),
            match.nffId(),
            fixture.kickoff(),
            match.kickoff());
    return new Fixture(
        fixture.id(),
        fixture.homeId(),
        fixture.awayId(),
        fixture.home(),
        fixture.away(),
        fixture.tournamentId(),
        fixture.competition(),
        fixture.seasonId(),
        match.kickoff(),
        endedAt,
        basis);
  }

  /** La finestra viene selezionata su NFF prima di associare gli ID utilizzati per le quote. */
  public List<Fixture> targets(
      List<Fixture> fixtures, List<Match> schedule, Instant cutoff, Instant end) {
    Map<String, Fixture> targets = new LinkedHashMap<>();
    for (var match : schedule) {
      if (!match.kickoff().isAfter(cutoff) || !match.kickoff().isBefore(end)) continue;
      var candidates = fixtures.stream().filter(f -> sameEvent(f, match)).toList();
      if (candidates.size() != 1)
        throw new IllegalArgumentException(
            "Calendario provider non univoco per NFF "
                + match.nffId()
                + " ("
                + match.home()
                + " - "
                + match.away()
                + ", "
                + match.kickoff()
                + "): "
                + candidates.size()
                + " corrispondenze; cache conservata");
      var f = reconcile(candidates.getFirst(), schedule);
      var target =
          new Fixture(
              f.id(),
              f.homeId(),
              f.awayId(),
              f.home(),
              f.away(),
              f.tournamentId(),
              f.competition(),
              f.seasonId(),
              f.kickoff(),
              null,
              "NFF_SCHEDULE");
      if (targets.putIfAbsent(f.id(), target) != null)
        throw new IllegalArgumentException("Identita provider associata a piu incontri NFF");
    }
    if (targets.isEmpty()) throw new IllegalArgumentException("Nessuna partita nella finestra NFF");
    return targets.values().stream()
        .sorted(Comparator.comparing(Fixture::kickoff).thenComparing(Fixture::id))
        .toList();
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
