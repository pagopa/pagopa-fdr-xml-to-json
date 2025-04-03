package it.gov.pagopa.fdrxmltojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueMessage {

    @JsonProperty("ContainerName")
    private String containerName;

    @JsonProperty("BlobName")
    private String fileName;
}