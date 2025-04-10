package it.gov.pagopa.fdrxmltojson.model;

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
