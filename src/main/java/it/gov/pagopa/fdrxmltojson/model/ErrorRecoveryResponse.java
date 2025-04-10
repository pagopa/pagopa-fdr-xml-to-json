package it.gov.pagopa.fdrxmltojson.model;

import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorRecoveryResponse {

    String status;
    String description;
}
