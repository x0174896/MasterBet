package it.masterbet.ingestion.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.*;
import java.util.*;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class OddsPapiHttpTransportTest {
  private static final String PRIVATE_KEY = "private-test-key";

  @SuppressWarnings("unchecked")
  private HttpResponse<String> response(
      int status, String body, Map<String, List<String>> headers) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
    return response;
  }

  private HttpResponse<String> response(int status, String body) {
    return response(status, body, Map.of());
  }

  @Test
  void honoursProviderRetryMillisecondsAndHeaderBeforeContinuingTheSameRequest() throws Exception {
    var http = mock(HttpClient.class);
    var waits = new ArrayList<Long>();
    var transport = new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, http, waits::add);
    var limited =
        response(
            429,
            "{\"error\":{\"code\":\"RATE_LIMITED\",\"retryMs\":7000}}",
            Map.of("Retry-After", List.of("8")));
    var success = response(200, "{\"fixtureId\":\"f\"}");
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(limited, success);
    assertEquals(
        "{\"fixtureId\":\"f\"}", transport.get("historical-odds", Map.of("fixtureId", "f")));
    assertEquals(List.of(8000L), waits);
    verify(http, times(2))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  @Test
  void neverRetriesAnExhaustedQuotaOrInvalidCredentials() throws Exception {
    for (var response :
        List.of(
            response(429, "{\"code\":\"REQUEST_LIMIT_EXCEEDED\"}"),
            response(401, "{\"error\":\"invalid key\"}"))) {
      var http = mock(HttpClient.class);
      var transport =
          new OddsPapiHttpTransport(
              new ObjectMapper(),
              PRIVATE_KEY,
              http,
              milliseconds -> fail("Nessuna attesa prevista"));
      when(http.send(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
          .thenReturn(response);
      var error =
          assertThrows(
              IllegalStateException.class, () -> transport.get("historical-odds", Map.of()));
      assertFalse(error.getMessage().contains(PRIVATE_KEY));
      verify(http, times(1))
          .send(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
  }

  @Test
  void boundsRateLimitRetriesAndRefusesExcessiveWaits() throws Exception {
    var http = mock(HttpClient.class);
    var limited = response(429, "{\"error\":{\"code\":\"RATE_LIMITED\",\"retryMs\":7000}}");
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(limited);
    var waits = new ArrayList<Long>();
    var transport = new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, http, waits::add);
    var error =
        assertThrows(IllegalStateException.class, () -> transport.get("historical-odds", Map.of()));
    assertTrue(error.getMessage().contains("tentativi esauriti"));
    assertEquals(List.of(7000L, 7000L), waits);
    verify(http, times(3))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var slow = mock(HttpClient.class);
    var longWait = response(429, "{\"error\":{\"code\":\"RATE_LIMITED\",\"retryMs\":60000}}");
    when(slow.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(longWait);
    var bounded =
        new OddsPapiHttpTransport(
            new ObjectMapper(),
            PRIVATE_KEY,
            slow,
            milliseconds -> fail("Non attendere oltre il limite"));
    assertThrows(IllegalStateException.class, () -> bounded.get("historical-odds", Map.of()));
    verify(slow, times(1))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  @Test
  void retriesFreeNetworkFailuresButNeverRepeatsPotentiallyBilledRequests() throws Exception {
    var free = mock(HttpClient.class);
    var success = response(200, "{}");
    when(free.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new HttpTimeoutException("URI privata con apiKey=" + PRIVATE_KEY))
        .thenReturn(success);
    assertEquals(
        "{}",
        new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, free, ms -> {})
            .get("account", Map.of()));
    verify(free, times(2))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var paid = mock(HttpClient.class);
    when(paid.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("URI privata con apiKey=" + PRIVATE_KEY));
    var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, paid, ms -> {})
                    .get("fixtures", Map.of()));
    assertFalse(error.getMessage().contains(PRIVATE_KEY));
    assertTrue(error.getMessage().contains("IOException"));
    verify(paid, times(1))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  @Test
  void doesNotRetryTlsValidationAndPreservesCancellation() throws Exception {
    var http = mock(HttpClient.class);
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new SSLHandshakeException("apiKey=" + PRIVATE_KEY));
    var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, http, ms -> {})
                    .get("account", Map.of()));
    assertTrue(error.getMessage().contains("TLS"));
    assertFalse(error.getMessage().contains(PRIVATE_KEY));
    verify(http, times(1))
        .send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var cancelled = mock(HttpClient.class);
    when(cancelled.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new InterruptedException());
    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              new OddsPapiHttpTransport(new ObjectMapper(), PRIVATE_KEY, cancelled, ms -> {})
                  .get("account", Map.of()));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
