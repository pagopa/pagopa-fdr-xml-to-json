package it.gov.pagopa.fdrxmltojson.util;

public class FormatterUtil {

    public static String messageFormat(String sessionId, String invocationId, String pspId, String fileName, String message, Object... args) {
        String suffix = String.format(" [sessionId: %s][invocationId: %s][psp: %s][filename: %s]", sessionId, invocationId, pspId, fileName);
        return String.format(message, args) + suffix;
    }
}
