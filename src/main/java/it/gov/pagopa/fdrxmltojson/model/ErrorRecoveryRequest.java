package it.gov.pagopa.fdrxmltojson.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorRecoveryRequest {

    @NotEmpty
    String partitionKey;
    List<String> rowKeys;
}
