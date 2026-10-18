package it.masterbet.shared;

import java.util.NoSuchElementException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ProblemDetail malformedBody() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Richiesta JSON non valida");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ProblemDetail conflict(org.springframework.web.server.ResponseStatusException e) {
    return ProblemDetail.forStatusAndDetail(
        e.getStatusCode(), e.getReason() == null ? "Richiesta non valida" : e.getReason());
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail missing(NoSuchElementException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail invalid(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Richiesta o dati non validi");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail failure(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Operazione non riuscita. Verificare lo stato dell'importazione locale.");
  }
}
