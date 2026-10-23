package com.mycompany.agentesp2.model.grid;

import org.json.JSONObject;

/**
 * Encapsula la lectura del sensor de autonomía 'grid.energy'.
 * 
 * En los retos de la Práctica 2, la energía es FINITA (mode = "FINITE").
 * Cada movimiento cardinal aplicado consume 1 unidad de energía.
 * Si el balance llega a 0 antes de pisar la celda objetivo, el servidor termina
 * la simulación con control.runStatus = "FAILED".
 */
public class GridEnergy {

    private final String mode;
    private final int remaining;

    public GridEnergy(String mode, int remaining) {
        this.mode = mode != null ? mode : "FINITE";
        this.remaining = remaining;
    }

    /**
     * Construye una instancia a partir del JSON devuelto por el sensor 'grid.energy'.
     */
    public static GridEnergy fromJson(JSONObject json) {
        if (json == null) {
            return new GridEnergy("FINITE", 0);
        }
        JSONObject val = json.has("value") ? json.getJSONObject("value") : json;
        String mode = val.optString("mode", "FINITE");
        int remaining = val.optInt("remaining", 0);
        return new GridEnergy(mode, remaining);
    }

    public String getMode() {
        return mode;
    }

    public int getRemaining() {
        return remaining;
    }

    /**
     * Indica si el agente aún dispone de energía para continuar moviéndose.
     */
    public boolean hasEnergy() {
        return remaining > 0;
    }

    @Override
    public String toString() {
        return String.format("%d unidades (%s)", remaining, mode);
    }
}
