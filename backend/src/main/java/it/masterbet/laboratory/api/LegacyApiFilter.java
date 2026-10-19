package it.masterbet.laboratory.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** The old single-league write endpoints cannot bypass the laboratory administrator. */
@Component
public class LegacyApiFilter extends OncePerRequestFilter {
  private final boolean enabled;

  public LegacyApiFilter(@Value("${masterbet.legacy-api-enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!enabled
        && (path.equals("/api/scenario")
            || path.startsWith("/api/matches/")
            || path.startsWith("/api/betting")
            || path.equals("/api/advance-job"))) {
      response.setStatus(410);
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write(
              "{\"title\":\"Archivio precedente\",\"status\":410,\"detail\":\"Usare il laboratorio"
                  + " multicampionato /api/lab\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
