package it.masterbet.laboratory.application;

import it.masterbet.laboratory.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class LabCommand implements ApplicationRunner {
  private final LabJobs jobs;
  private final LabDownloadService downloads;
  private final LabSimulationService simulation;
  private final ConfigurableApplicationContext context;
  private final LabLegacyReset reset;

  public LabCommand(
      LabJobs jobs,
      LabDownloadService downloads,
      LabSimulationService simulation,
      ConfigurableApplicationContext context,
      LabLegacyReset reset) {
    this.jobs = jobs;
    this.downloads = downloads;
    this.simulation = simulation;
    this.context = context;
    this.reset = reset;
  }

  @Override
  public void run(ApplicationArguments args) {
    String action =
        args.containsOption("lab-action") ? args.getOptionValues("lab-action").getFirst() : null;
    if (action == null) return;
    if (action.equals("reset-legacy")) {
      System.out.println("MASTERBET_LAB_RESET " + reset.reset() + " righe archiviate");
      context.close();
      return;
    }
    var from = date(args, "lab-from");
    var to = date(args, "lab-to");
    var competitions =
        args.containsOption("lab-competitions")
            ? Arrays.stream(args.getOptionValues("lab-competitions").getFirst().split(","))
                .map(Integer::valueOf)
                .toList()
            : List.<Integer>of();
    var request =
        new LabDownloadRequest(competitions, from, to, null, args.containsOption("lab-refresh"));
    if (action.equals("campaign")) {
      var results = jobs.start("results", downloads.results(request));
      var quotes = jobs.start("quotes", downloads.quotes(request));
      var resultsStatus = jobs.await(results.id());
      var quoteStatus = jobs.await(quotes.id());
      System.out.println(
          "MASTERBET_LAB_DOWNLOADS risultati="
              + resultsStatus.status()
              + " quote="
              + quoteStatus.status());
      if (!Set.of("SUCCEEDED", "PARTIAL").contains(resultsStatus.status())
          || !Set.of("SUCCEEDED", "PARTIAL").contains(quoteStatus.status())) {
        context.close();
        throw new IllegalStateException(
            "MASTERBET_LAB_CAMPAIGN_DOWNLOAD_FAILED: consultare i job persistiti");
      }
      if (Set.of("SUCCEEDED", "PARTIAL").contains(resultsStatus.status())
          && Set.of("SUCCEEDED", "PARTIAL").contains(quoteStatus.status())) {
        var calculated =
            jobs.blocking(
                "calculate",
                simulation.calculate(
                    new LabRunConfig(
                        request.from(), request.to(), competitions, null, "HISTORICAL")));
        System.out.println("MASTERBET_LAB_CAMPAIGN " + calculated.status());
        if (!calculated.status().equals("SUCCEEDED")) {
          context.close();
          throw new IllegalStateException(
              "MASTERBET_LAB_CALCULATION_FAILED: consultare i job persistiti");
        }
      }
      context.close();
      return;
    }
    var operation =
        switch (action) {
          case "catalog" -> downloads.catalog(request.refresh());
          case "results" -> downloads.results(request);
          case "quotes" -> downloads.quotes(request);
          case "calculate" ->
              simulation.calculate(
                  new LabRunConfig(request.from(), request.to(), competitions, null, "HISTORICAL"));
          default -> throw new IllegalArgumentException("Azione sconosciuta");
        };
    var status = jobs.blocking(action, operation);
    System.out.println(
        "MASTERBET_LAB "
            + status.status()
            + " "
            + status.completed()
            + " elementi; "
            + status.errors().size()
            + " segnalazioni; job="
            + status.id());
    context.close();
    if (status.status().equals("FAILED") || status.status().equals("INTERRUPTED"))
      throw new IllegalStateException("MASTERBET_LAB_FAILED: " + status.id());
  }

  private LocalDate date(ApplicationArguments args, String key) {
    return args.containsOption(key) ? LocalDate.parse(args.getOptionValues(key).getFirst()) : null;
  }
}
