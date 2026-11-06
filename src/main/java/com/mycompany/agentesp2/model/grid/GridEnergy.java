package com.mycompany.agentesp2.model.grid;

import org.json.JSONObject;

/**
 * Encapsula la lectura del sensor de energía 'grid.energy'.
 *
 * En los retos de navegación con energía finita, mode vale "FINITE" y remaining indica
 * los movimientos restantes disponibles. Cada movimiento aplicado consume 1 unidad.
 */
public final class GridEnergy {

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
            throw new IllegalArgumentException("El JSON de energía no puede ser nulo.");
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
     * Indica si queda energía disponible para realizar al menos un movimiento más.
     */
    public boolean hasEnergy() {
        if (!"FINITE".equalsIgnoreCase(mode)) {
            return true;
        }
        return remaining > 0;
    }

    @Override
    public String toString() {
        return String.format("%d (%s)", remaining, mode);
    }
}
