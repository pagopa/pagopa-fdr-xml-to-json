package it.gov.pagopa.fdrxmltojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlobData {
    private String fileName;
    private byte[] content;
    private Map<String, String> metadata;
}
