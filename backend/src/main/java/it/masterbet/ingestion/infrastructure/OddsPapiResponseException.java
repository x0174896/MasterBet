package it.masterbet.ingestion.infrastructure;

/** Safe transport metadata only: never the authenticated URL or response body. */
public class OddsPapiResponseException extends IllegalStateException {
  private final int status;
  private final String code;

  public OddsPapiResponseException(int status, String code, String endpoint) {
    super(
        "OddsPapi "
            + endpoint
            + ": HTTP "
            + status
            + " ("
            + code
            + (code.equals("RATE_LIMITED") ? ", tentativi esauriti" : "")
            + "). Cache conservata");
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
