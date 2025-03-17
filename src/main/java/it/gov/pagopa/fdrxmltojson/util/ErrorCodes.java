package it.gov.pagopa.fdrxmltojson.util;

import lombok.Getter;

@Getter
public enum ErrorCodes {
  // Function FDRXMLERROR errors
  FDR1XMLERROR_E1("FDRXMLERROR-E1", "Generic Error in XmlErrorRetry function"),
  FDR1XMLERROR_E2("FDRXMLERROR-E2", "Failed Psp API internalDelete call"),
  FDR1XMLERROR_E3("FDRXMLERROR-E3", "No entity found with the provided partitionKey and rowKey"),
  FDR1XMLERROR_E4("FDRXMLERROR-E4", "I/O error occurs during blob download");

  private final String code;
  private final String message;

  ErrorCodes(String code, String message) {
    this.code = code;
    this.message = message;
  }
}
