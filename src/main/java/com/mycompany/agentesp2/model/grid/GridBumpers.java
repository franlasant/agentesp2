package com.mycompany.agentesp2.model.grid;

import org.json.JSONObject;

/**
 * Encapsula la lectura del sensor de proximidad 'grid.bumpers'.
 *
 * Informa si las 4 casillas adyacentes inmediatas están bloqueadas (true) por un obstáculo
 * o por el borde del mundo, o si están despejadas (false).
 *
 * Intentar moverse hacia una dirección con bumper == true provoca colisión y fallo inmediato (FAILED).
 */
public final class GridBumpers {

    private final boolean north;
    private final boolean east;
    private final boolean south;
    private final boolean west;

    public GridBumpers(boolean north, boolean east, boolean south, boolean west) {
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
    }

    /**
     * Construye una instancia a partir del JSON devuelto por el sensor 'grid.bumpers'.
     */
    public static GridBumpers fromJson(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("El JSON de bumpers no puede ser nulo.");
        }
        JSONObject val = json.has("value") ? json.getJSONObject("value") : json;
        boolean n = val.getBoolean("north");
        boolean e = val.getBoolean("east");
        boolean s = val.getBoolean("south");
        boolean w = val.getBoolean("west");
        return new GridBumpers(n, e, s, w);
    }

    /**
     * Indica si una dirección de movimiento está bloqueada por obstáculo o límite del mapa.
     */
    public boolean isBlocked(GridAction action) {
        if (action == null) {
            return true;
        }
        return switch (action) {
            case MOVE_NORTH -> north;
            case MOVE_EAST -> east;
            case MOVE_SOUTH -> south;
            case MOVE_WEST -> west;
        };
    }

    /**
     * Indica si una dirección de movimiento está libre y es segura para avanzar sin chocar.
     */
    public boolean isSafe(GridAction action) {
        return !isBlocked(action);
    }

    public boolean isNorthBlocked() {
        return north;
    }

    public boolean isEastBlocked() {
        return east;
    }

    public boolean isSouthBlocked() {
        return south;
    }

    public boolean isWestBlocked() {
        return west;
    }

    @Override
    public String toString() {
        return String.format("[N:%s, E:%s, S:%s, W:%s]",
                north ? "MURO" : "libre",
                east ? "MURO" : "libre",
                south ? "MURO" : "libre",
                west ? "MURO" : "libre");
    }
}
