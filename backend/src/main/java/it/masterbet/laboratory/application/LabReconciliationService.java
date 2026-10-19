package it.masterbet.laboratory.application;

import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.LabRepository;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class LabReconciliationService {
  private final LabRepository repo;
  private final LabDownloadService downloads;
  private final FixtureMatcher matcher;

  public LabReconciliationService(
      LabRepository repo, LabDownloadService downloads, FixtureMatcher matcher) {
    this.repo = repo;
    this.downloads = downloads;
    this.matcher = matcher;
  }

  public Consumer<LabJobs.Progress> reconcile(LabDownloadRequest request) {
    return p -> {
      var selected = downloads.selected(request);
      p.total(selected.size());
      Instant from = request.from().atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant to = request.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      var all = repo.matches();
      for (var c : selected) {
        var official =
            all.stream()
                .filter(
                    m ->
                        !m.cup()
                            && !m.fixture().id().startsWith("odds:")
                            && m.fixture().tournamentId() == c.id()
                            && !m.fixture().kickoff().isBefore(from)
                            && m.fixture().kickoff().isBefore(to))
                .toList();
        if (official.isEmpty()) {
          p.warning(
              "GAP: "
                  + c.name()
                  + ": nessun calendario risultati nel periodo; dati conservati, campionato"
                  + " escluso");
          p.item(c.name() + ": calendario risultati assente");
          continue;
        }
        p.update("Calendario e riconciliazione " + c.name());
        var provider = downloads.calendar(c, request);
        var result = matcher.align(official, provider);
        int done = 0;
        for (var m : official) {
          var target = result.matches().get(m.fixture().id());
          if (target != null) repo.link(m, target, result.evidence().get(m.fixture().id()));
          if (++done % 25 == 0)
            p.update(c.name() + ": riconciliati " + done + " / " + official.size() + " incontri");
        }
        int missing = official.size() - result.matches().size();
        if (missing > 0)
          p.warning(
              "GAP: "
                  + c.name()
                  + ": "
                  + missing
                  + " incontri senza identita univoca o annullati; nessun abbinamento forzato");
        p.item(
            c.name()
                + ": "
                + result.matches().size()
                + " abbinamenti verificati; "
                + missing
                + " esclusioni");
      }
    };
  }
}
