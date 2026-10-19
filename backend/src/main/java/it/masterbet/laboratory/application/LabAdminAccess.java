package it.masterbet.laboratory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabAdminAccess {
  private record Credential(String salt, String hash) {}

  private record Session(Instant expires) {}

  private final Path credentialFile;
  private final ObjectMapper mapper;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Map<String, List<Instant>> failures = new ConcurrentHashMap<>();

  public LabAdminAccess(@Value("${masterbet.data-dir}") String directory, ObjectMapper mapper) {
    credentialFile = Path.of(directory).resolve("private/admin-credential.json");
    this.mapper = mapper;
  }

  public Map<String, Boolean> status(HttpServletRequest request) {
    return Map.of(
        "configured", Files.exists(credentialFile), "authenticated", authenticated(request));
  }

  public void require(HttpServletRequest request) {
    if (!authenticated(request))
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Accesso amministratore richiesto");
  }

  public void mutation(HttpServletRequest request) {
    if (!"1".equals(request.getHeader("X-MasterBet-Admin")))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Intestazione amministrativa richiesta");
    String origin = request.getHeader("Origin");
    if (origin != null) {
      try {
        var uri = java.net.URI.create(origin);
        if (uri.getScheme() == null
            || uri.getHost() == null
            || !Set.of("http", "https").contains(uri.getScheme())
            || uri.getUserInfo() != null
            || !Set.of("127.0.0.1", "localhost").contains(uri.getHost())
            || !uri.getRawAuthority().equals(request.getHeader("Host")))
          throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origine non ammessa");
      }
    }
  }

  public synchronized void login(
      String password, boolean setup, HttpServletRequest request, HttpServletResponse response) {
    mutation(request);
    String remote = request.getRemoteAddr();
    Instant now = Instant.now();
    if (failures.size() > 1000) failures.clear();
    var attempts = failures.computeIfAbsent(remote, k -> new ArrayList<>());
    attempts.removeIf(t -> t.isBefore(now.minusSeconds(900)));
    if (attempts.size() >= 5)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Troppi tentativi; riprovare fra 15 minuti");
    if (password == null || password.length() < 12 || password.length() > 256)
      throw new IllegalArgumentException("Password da 12 a 256 caratteri");
    try {
      if (setup) {
        if (Files.exists(credentialFile))
          throw new ResponseStatusException(HttpStatus.CONFLICT, "Amministratore gia configurato");
        if (!java.net.InetAddress.getByName(remote).isLoopbackAddress())
          throw new ResponseStatusException(
              HttpStatus.FORBIDDEN, "Configurazione iniziale solo locale");
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        var credential =
            new Credential(
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derive(password, salt)));
        Files.createDirectories(credentialFile.getParent());
        Files.writeString(
            credentialFile, mapper.writeValueAsString(credential), StandardOpenOption.CREATE_NEW);
      } else {
        if (!Files.exists(credentialFile))
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "Configurare prima l'amministratore");
        var credential = mapper.readValue(Files.readString(credentialFile), Credential.class);
        if (!MessageDigest.isEqual(
            Base64.getDecoder().decode(credential.hash()),
            derive(password, Base64.getDecoder().decode(credential.salt())))) {
          attempts.add(now);
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
        }
      }
      attempts.clear();
      sessions.entrySet().removeIf(e -> e.getValue().expires().isBefore(now));
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      sessions.put(token, new Session(now.plusSeconds(28800)));
      cookie(response, token, 28800, request.isSecure());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Configurazione amministratore non disponibile");
    }
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    mutation(request);
    sessions.remove(token(request));
    cookie(response, "", 0, request.isSecure());
  }

  private boolean authenticated(HttpServletRequest request) {
    var session = sessions.get(token(request));
    return session != null && session.expires().isAfter(Instant.now());
  }

  private String token(HttpServletRequest request) {
    if (request.getCookies() != null)
      for (var c : request.getCookies())
        if (c.getName().equals("masterbet-admin")) return c.getValue();
    return "";
  }

  private void cookie(HttpServletResponse response, String value, int maxAge, boolean secure) {
    var cookie = new Cookie("masterbet-admin", value);
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setAttribute("SameSite", "Strict");
    cookie.setPath("/api/lab");
    cookie.setMaxAge(maxAge);
    response.addCookie(cookie);
  }

  private byte[] derive(String password, byte[] salt) {
    try {
      var spec = new PBEKeySpec(password.toCharArray(), salt, 310000, 256);
      try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .getEncoded();
      } finally {
        spec.clearPassword();
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Autenticazione non disponibile");
    }
  }
}
