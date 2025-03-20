package it.gov.pagopa.fdrxmltojson.model;

public class AppConstant {
    public static final String columnFieldSessionId = "sessionId";
    public static final String columnFieldCreated = "created";
    public static final String columnFieldFileName = "fileName";
    public static final String columnFieldFdr = "fdr";
    public static final String columnFieldPspId = "pspId";
    public static final String columnFieldErrorType = "errorType";
    public static final String columnFieldHttpErrorCode = "httpErrorCode";
    public static final String columnFieldHttpEventType = "httpEventType";
    public static final String columnFieldHttpErrorResponse = "httpErrorResponse";
    public static final String columnFieldStackTrace = "stackTrace";
    public static final String columnFieldRetryAttempt = "retryAttempt";

    public static final String FDR_FLOW_NOT_FOUND = "FDR-3001";
    public static final String FDR_FLOW_ALREADY_CREATED = "FDR-3002";
    public static final String FDR_PAYMENT_ALREADY_ADDED = "FDR-3006";
}
