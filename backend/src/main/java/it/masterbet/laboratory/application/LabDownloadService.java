package it.masterbet.laboratory.application;

import it.masterbet.football.domain.Fixture;
import it.masterbet.football.infrastructure.OddsPapiFixtureDownloader;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.ingestion.infrastructure.OddsPapiResponseException;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import it.masterbet.odds.infrastructure.OddsPapiHistoricalOddsDownloader;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class LabDownloadService {
  private final LabRepository repo;
  private final CalcioSource calcio;
  private final CompetitionMatcher competitionMatcher;
  private final FixtureMatcher fixtureMatcher;
  private final OddsPapiClient odds;
  private final OddsPapiFixtureDownloader fixtures;
  private final OddsPapiHistoricalOddsDownloader prices;
  private final LabDownloadCheckpoints checkpoints;

  public LabDownloadService(
      LabRepository repo,
      CalcioSource calcio,
      CompetitionMatcher competitionMatcher,
      FixtureMatcher fixtureMatcher,
      OddsPapiClient odds,
      OddsPapiFixtureDownloader fixtures,
      OddsPapiHistoricalOddsDownloader prices,
      LabDownloadCheckpoints checkpoints) {
    this.repo = repo;
    this.calcio = calcio;
    this.competitionMatcher = competitionMatcher;
    this.fixtureMatcher = fixtureMatcher;
    this.odds = odds;
    this.fixtures = fixtures;
    this.prices = prices;
    this.checkpoints = checkpoints;
  }

  public Consumer<LabJobs.Progress> catalog(boolean refresh) {
    return p -> {
      p.update("Cataloghi Calcio.com e OddsPapi");
      var source = calcio.catalog(refresh);
      var tournaments =
          refresh
              ? odds.refresh("tournaments", Map.of("sportId", "10"))
              : odds.get("tournaments", Map.of("sportId", "10"));
      var matched = competitionMatcher.match(source, tournaments);
      p.total(matched.size());
      for (var c : matched) {
        repo.save(c);
        p.item(c.country() + " / " + c.name() + ": " + c.coverage());
      }
    };
  }

  public Consumer<LabJobs.Progress> results(LabDownloadRequest request) {
    return p -> {
      var selected = selected(request);
      if (selected.isEmpty())
        throw new IllegalArgumentException("Nessun campionato comune: aggiornare il catalogo");
      Set<Long> teams = new HashSet<>();
      for (var c : selected) importCompetition(c, request, false, teams, request.from(), p);
      var warmup =
          repo.matches().stream()
              .filter(
                  m ->
                      !m.cup()
                          && selected.stream().anyMatch(c -> c.id() == m.fixture().tournamentId()))
              .map(m -> m.fixture().kickoff().atZone(ZoneOffset.UTC).toLocalDate())
              .min(Comparator.naturalOrder())
              .orElse(request.from())
              .minusDays(90);
      // Coppe di tutte le squadre coinvolte; nessuna amichevole o selezione nazionale.
      for (var c : repo.competitions())
        if (c.cup()) importCompetition(c, request, true, teams, warmup, p);
    };
  }

  private void importCompetition(
      LabCompetition c,
      LabDownloadRequest request,
      boolean cups,
      Set<Long> teams,
      LocalDate seasonFrom,
      LabJobs.Progress p) {
    for (String season : c.seasons()) {
      if (!seasonOverlaps(season, seasonFrom, request.to())) continue;
      try {
        var rounds = calcio.rounds(c, season, true);
        if (rounds.isEmpty()) {
          var rows = calcio.schedule(c, season, true);
          if (rows.isEmpty()) {
            p.warning(
                "GAP: "
                    + c.slug()
                    + " "
                    + season
                    + ": calendario fonte vuoto anche senza giornata");
          }
          importRows(
              c, season, "Calendario senza giornate", rows, request, cups, teams, seasonFrom, p);
        }
        for (var round : rounds) {
          p.update(c.name() + " " + season + " / giornata " + round.path("roundName").asText());
          var rows = calcio.schedule(c, season, round.required("roundId").asLong(), true);
          importRows(
              c,
              season,
              round.path("roundName").asText(),
              rows,
              request,
              cups,
              teams,
              seasonFrom,
              p);
        }
      } catch (Exception e) {
        p.error(c.slug() + " " + season + ": " + LabJobs.safe(e));
      }
    }
  }

  private void importRows(
      LabCompetition c,
      String season,
      String round,
      com.fasterxml.jackson.databind.JsonNode rows,
      LabDownloadRequest request,
      boolean cups,
      Set<Long> teams,
      LocalDate seasonFrom,
      LabJobs.Progress p) {
    for (var row : rows) {
      Instant scheduled = Instant.parse(row.required("date").asText());
      if (cups && scheduled.isBefore(seasonFrom.atStartOfDay().toInstant(ZoneOffset.UTC))) continue;
      if (scheduled.isAfter(request.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
        continue;
      if (cups
          && !teams.contains(row.path("home").path("id").asLong())
          && !teams.contains(row.path("away").path("id").asLong())) continue;
      String id = "calcio:" + row.required("id").asLong();
      var old = repo.match(id);
      if (old.isPresent() && old.get().score() != null && !request.refresh()) {
        if (!cups) {
          teams.add(old.get().fixture().homeId());
          teams.add(old.get().fixture().awayId());
        }
        continue;
      }
      try {
        var value = calcio.detail(c, season, round, row, request.refresh() || old.isPresent());
        if (old.isPresent() && old.get().oddsFixtureId() != null)
          value = value.linked(old.get().oddsFixtureId());
        repo.save(value);
        if (!cups) {
          teams.add(value.fixture().homeId());
          teams.add(value.fixture().awayId());
        }
        if (value.fixture().finishedBefore(Instant.now()) && value.score() == null)
          p.warning("GAP: " + id + ": HT/FT mancanti, escluso dai risultati");
        p.item(c.name() + " / " + value.fixture().home() + " - " + value.fixture().away());
      } catch (Exception e) {
        p.error(id + ": " + LabJobs.safe(e));
      }
    }
  }

  public Consumer<LabJobs.Progress> quotes(LabDownloadRequest request) {
    return p -> {
      var selected = selected(request);
      if (selected.isEmpty()) throw new IllegalArgumentException("Nessun campionato comune");
      Instant from = request.from().atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant to = request.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      for (var competition : selected) {
        var official = repo.matches();
        p.update("Calendario OddsPapi: " + competition.name());
        List<Fixture> provider;
        try {
          provider = calendar(competition, request);
        } catch (Exception e) {
          p.error(competition.slug() + ": " + LabJobs.safe(e));
          if (fatal(e)) throw e;
          continue;
        }
        var eligible =
            official.stream()
                .filter(
                    m ->
                        !m.cup()
                            && !m.fixture().id().startsWith("odds:")
                            && m.fixture().tournamentId() == competition.id()
                            && !m.fixture().kickoff().isBefore(from)
                            && m.fixture().kickoff().isBefore(to))
                .toList();
        var alignment = fixtureMatcher.align(eligible, provider);
        // L'ordine dei download resta libero: gli ID OddsPapi vengono acquisiti anche senza HT/FT.
        Set<String> acquired = new HashSet<>();
        for (var match : eligible) {
          if (match.fixture().id().startsWith("odds:")) continue;
          try {
            var linked = Optional.ofNullable(alignment.matches().get(match.fixture().id()));
            if (linked.isEmpty()) {
              p.warning("GAP: " + match.fixture().id() + ": nessun abbinamento calendario univoco");
              continue;
            }
            Fixture f = linked.get();
            if (!acquired.add(f.id()))
              throw new IllegalArgumentException(
                  "Identificativo provider associato a piu incontri");
            repo.link(
                match,
                f,
                alignment
                    .evidence()
                    .getOrDefault(match.fixture().id(), List.of("Nomi verificati")));
            if (alreadyImported(f, request)) {
              p.item("Quote gia importate o assenza gia verificata: " + f.id());
              continue;
            }
            int imported = importHistory(f, match.fixture().id(), request, p);
            p.item(
                "Quote "
                    + competition.name()
                    + " / "
                    + match.fixture().home()
                    + " - "
                    + match.fixture().away()
                    + ": "
                    + imported
                    + " esiti");
          } catch (Exception e) {
            p.error(match.fixture().id() + ": " + LabJobs.safe(e));
            if (fatal(e)) throw e;
          }
        }
        // Se risultati non ancora scaricati, archivia soltanto il calendario del provider.
        for (var f : provider) {
          if (acquired.contains(f.id())) continue;
          var mapped =
              new Fixture(
                  "odds:" + f.id(),
                  f.homeId(),
                  f.awayId(),
                  f.home(),
                  f.away(),
                  competition.id(),
                  competition.name(),
                  f.seasonId(),
                  f.kickoff(),
                  f.endedAt(),
                  f.timingBasis());
          var stub =
              new LabMatch(
                  mapped,
                  "ODDSPAPI:" + f.seasonId(),
                  "",
                  false,
                  null,
                  "https://oddspapi.io/en/docs/get-fixtures",
                  f.id(),
                  "METADATI_ODDSPAPI");
          if (repo.match(mapped.id()).isEmpty()) repo.save(stub);
          if (alreadyImported(f, request)) {
            p.item("Quote gia importate o assenza gia verificata: " + f.id());
            continue;
          }
          try {
            importHistory(f, mapped.id(), request, p);
            p.item("Quote archiviate in attesa dei risultati: " + f.home() + " - " + f.away());
          } catch (Exception e) {
            p.error(f.id() + ": " + LabJobs.safe(e));
            if (fatal(e)) throw e;
          }
        }
      }
    };
  }

  private boolean alreadyImported(Fixture fixture, LabDownloadRequest request) {
    return !request.refresh()
        && fixture.finishedBefore(Instant.now())
        && checkpoints
            .status(fixture.id(), request.bookmakers())
            .filter(s -> Set.of("NO_DATA", "IMPORTED").contains(s))
            .isPresent();
  }

  public List<Fixture> calendar(LabCompetition competition, LabDownloadRequest request) {
    Instant from = request.from().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = request.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var provider =
        request.refresh()
            ? parseFixtures(
                odds.refresh(
                    "fixtures",
                    Map.of(
                        "tournamentId",
                        competition.oddsTournamentId().toString(),
                        "from",
                        from.toString(),
                        "to",
                        to.toString())))
            : fixtures.tournament(competition.oddsTournamentId(), from, to);
    if (provider.stream().anyMatch(f -> f.tournamentId() != competition.oddsTournamentId()))
      throw new IllegalArgumentException("Calendario di un campionato differente");
    return provider.stream()
        .filter(f -> !f.kickoff().isBefore(from) && f.kickoff().isBefore(to))
        .toList();
  }

  private int importHistory(
      Fixture fixture, String targetId, LabDownloadRequest request, LabJobs.Progress progress) {
    var history = history(fixture, request, progress);
    for (var q : history)
      repo.save(
          new LabQuote(
              targetId, q.bookmaker(), q.market(), q.outcomeId(), q.outcome(), q.updates()));
    // DOWNLOADED certifies the raw response only; reuse requires every outcome to be saved.
    if (!history.isEmpty()) checkpoints.save(fixture.id(), request.bookmakers(), "IMPORTED");
    return history.size();
  }

  private List<LabQuote> history(
      Fixture fixture, LabDownloadRequest request, LabJobs.Progress progress) {
    try {
      var data =
          prices.downloadHistory(
              fixture,
              request.bookmakers(),
              request.refresh() || !fixture.finishedBefore(Instant.now()));
      checkpoints.save(
          fixture.id(), request.bookmakers(), data.isEmpty() ? "NO_DATA" : "DOWNLOADED");
      return data;
    } catch (OddsPapiResponseException e) {
      if (e.status() == 404) {
        checkpoints.save(fixture.id(), request.bookmakers(), "NO_DATA");
        progress.warning(
            fixture.id()
                + ": archivio quote assente per i bookmaker richiesti (404); ripetibile con"
                + " aggiornamento forzato");
        return List.of();
      }
      throw e;
    }
  }

  private boolean fatal(Exception e) {
    return e instanceof OddsPapiResponseException response
            && (response.status() == 401
                || response.status() == 403
                || response.status() == 429
                || response.status() >= 500)
        || LabJobs.safe(e).contains("Riserva")
        || LabJobs.safe(e).contains("Quota OddsPapi")
        || LabJobs.safe(e).startsWith("Connessione OddsPapi");
  }

  private List<Fixture> parseFixtures(com.fasterxml.jackson.databind.JsonNode data) {
    if (!data.isArray()) throw new IllegalArgumentException("Calendario non valido");
    List<Fixture> list = new ArrayList<>();
    for (var row : data)
      if (!row.path("statusName").asText().equals("Cancelled"))
        list.add(OddsPapiFixtureDownloader.parse(row));
    return List.copyOf(list);
  }

  public List<LabCompetition> selected(LabDownloadRequest r) {
    return repo.competitions().stream()
        .filter(
            c ->
                !c.cup()
                    && c.oddsTournamentId() != null
                    && c.seasons().stream().anyMatch(s -> seasonOverlaps(s, r.from(), r.to()))
                    && (r.competitionIds().isEmpty() || r.competitionIds().contains(c.id())))
        .toList();
  }

  public static boolean seasonOverlaps(String season, LocalDate from, LocalDate to) {
    if (!season.matches("\\d{4}(/\\d{4})?")) return false;
    int first = Integer.parseInt(season.substring(0, 4));
    int last = season.length() > 4 ? Integer.parseInt(season.substring(5)) : first;
    return first <= to.getYear() && last >= from.getYear();
  }
}
