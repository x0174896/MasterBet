package it.masterbet.laboratory.api;

import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lab")
public class LabController {
  private final LabQueryService query;
  private final LabRunRepository runs;
  private final LabJobs jobs;
  private final LabDownloadService downloads;
  private final LabSimulationService simulation;
  private final LabAdminAccess access;
  private final LabLegacyReset reset;

  public LabController(
      LabQueryService query,
      LabRunRepository runs,
      LabJobs jobs,
      LabDownloadService downloads,
      LabSimulationService simulation,
      LabAdminAccess access,
      LabLegacyReset reset) {
    this.query = query;
    this.runs = runs;
    this.jobs = jobs;
    this.downloads = downloads;
    this.simulation = simulation;
    this.access = access;
    this.reset = reset;
  }

  @GetMapping("/coverage")
  public List<LabQueryService.Coverage> coverage() {
    return query.coverage();
  }

  @GetMapping("/runs")
  public List<LabRunRepository.Run> runs() {
    return runs.runs();
  }

  @GetMapping("/workspace")
  public LabQueryService.Workspace workspace(
      @RequestParam String runId,
      @RequestParam(required = false) Instant cursor,
      @RequestParam(defaultValue = "false") boolean cups,
      @RequestParam(defaultValue = "0") int competition,
      @RequestParam(defaultValue = "0") int page) {
    return query.workspace(runId, cursor, cups, competition, page);
  }

  public record RevealRequest(String runId, String betId) {}

  @PostMapping("/reveal")
  public Map<String, String> reveal(@RequestBody RevealRequest body) {
    return Map.of("status", query.reveal(body.runId(), body.betId()));
  }

  @GetMapping("/statistics")
  public List<LabQueryService.FullStatistics> statistics(
      @RequestParam String runId,
      @RequestParam Instant cursor,
      @RequestParam(defaultValue = "false") boolean cups,
      @RequestParam(defaultValue = "0") int competition) {
    var checked = query.workspace(runId, cursor, cups, competition, 0);
    return query.fullStatistics(checked.cursor(), cups, competition);
  }

  @GetMapping("/admin/session")
  public Map<String, Boolean> session(HttpServletRequest request) {
    return access.status(request);
  }

  public record Login(String password, boolean setup) {}

  @PostMapping("/admin/login")
  public Map<String, Boolean> login(
      @RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
    access.login(body.password(), body.setup(), request, response);
    return Map.of("authenticated", true);
  }

  @PostMapping("/admin/logout")
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    access.logout(request, response);
  }

  @GetMapping("/admin/jobs")
  public List<LabJobs.Status> jobs(HttpServletRequest request) {
    access.require(request);
    return jobs.list();
  }

  @PostMapping("/admin/catalog")
  public LabJobs.Status catalog(
      @RequestParam(defaultValue = "false") boolean refresh, HttpServletRequest request) {
    check(request);
    return jobs.start("catalog", downloads.catalog(refresh));
  }

  @PostMapping("/admin/results")
  public LabJobs.Status results(@RequestBody LabDownloadRequest body, HttpServletRequest request) {
    check(request);
    return jobs.start("results", downloads.results(body));
  }

  @PostMapping("/admin/quotes")
  public LabJobs.Status quotes(@RequestBody LabDownloadRequest body, HttpServletRequest request) {
    check(request);
    return jobs.start("quotes", downloads.quotes(body));
  }

  @PostMapping("/admin/calculate")
  public LabJobs.Status calculate(@RequestBody LabRunConfig body, HttpServletRequest request) {
    check(request);
    return jobs.start("calculate", simulation.calculate(body));
  }

  @PostMapping("/admin/reset-legacy")
  public Map<String, Integer> reset(HttpServletRequest request) {
    check(request);
    return Map.of("archivedRows", reset.reset());
  }

  private void check(HttpServletRequest request) {
    access.require(request);
    access.mutation(request);
  }
}
