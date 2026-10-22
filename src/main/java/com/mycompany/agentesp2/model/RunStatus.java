package com.mycompany.agentesp2.model;

/**
 * Estados posibles de una ejecución (run) en el servidor DBA Lab.
 */
public enum RunStatus {
    ACTIVE,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    INTERRUPTED,
    UNKNOWN;

    /**
     * Convierte una cadena de texto devuelta por la API en su valor enum correspondiente.
     */
    public static RunStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return RunStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * Indica si el run sigue en ejecución activa.
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Indica si el run ha alcanzado un estado terminal y debe detenerse.
     */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
