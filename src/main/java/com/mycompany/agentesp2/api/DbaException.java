package com.mycompany.agentesp2.api;

/**
 * Excepción para errores devueltos por la API de DBA Lab o errores de comunicación HTTP.
 */
public class DbaException extends Exception {
    private final int statusCode;
    private final String errorCode;

    public DbaException(int statusCode, String errorCode, String message) {
        super(String.format("DBA Lab Error [%d - %s]: %s", statusCode, errorCode, message));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public DbaException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.errorCode = "CLIENT_ERROR";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
