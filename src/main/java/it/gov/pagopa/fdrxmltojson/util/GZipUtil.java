package it.gov.pagopa.fdrxmltojson.util;

import lombok.experimental.UtilityClass;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@UtilityClass
public class GZipUtil {

    public static boolean isGzip(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Invalid input data for decompression: empty file");
        }
        return content.length > 2 && content[0] == (byte) 0x1F && content[1] == (byte) 0x8B;
    }

    public static InputStream decompressGzip(byte[] compressedContent) throws IOException {
        return new GZIPInputStream(new ByteArrayInputStream(compressedContent));
    }
}


