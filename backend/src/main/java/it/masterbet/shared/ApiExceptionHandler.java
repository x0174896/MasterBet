package it.masterbet.shared;

import java.util.NoSuchElementException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
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
