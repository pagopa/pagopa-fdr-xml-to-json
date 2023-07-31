package it.gov.pagopa.fdrxmltojson;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class AppException extends RuntimeException {
  public AppException(String message, Throwable cause) {
    super(message, cause);
  }
}
