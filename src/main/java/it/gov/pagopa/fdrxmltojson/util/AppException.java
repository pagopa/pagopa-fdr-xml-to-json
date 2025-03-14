package it.gov.pagopa.fdrxmltojson.util;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
  public AppException(String message, Throwable cause) {
    super(message, cause);
  }
}
