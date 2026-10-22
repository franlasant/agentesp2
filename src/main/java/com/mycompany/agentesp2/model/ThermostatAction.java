package com.mycompany.agentesp2.model;

/**
 * Acciones físicas disponibles para el entorno del Termostato con tipado seguro.
 */
public enum ThermostatAction {
    REFRIGERATION_ON("thermostat.refrigeration.on"),
    REFRIGERATION_OFF("thermostat.refrigeration.off");

    private final String actionCode;

    ThermostatAction(String actionCode) {
        this.actionCode = actionCode;
    }

    /**
     * Devuelve el código literal requerido por la API de DBA Lab.
     */
    public String getActionCode() {
        return actionCode;
    }

    @Override
    public String toString() {
        return actionCode;
    }
}
