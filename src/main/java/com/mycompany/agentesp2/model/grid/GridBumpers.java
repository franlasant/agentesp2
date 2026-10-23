package com.mycompany.agentesp2.model.grid;

import org.json.JSONObject;

/**
 * Encapsula la lectura del sensor de proximidad 'grid.bumpers'.
 * 
 * Informa si las 4 casillas adyacentes inmediatas al agente están bloqueadas (true)
 * o libres de obstáculos (false).
 * 
 * ¡CRUCIAL PARA LA SUPERVIVENCIA DEL AGENTE!
 * Según la especificación oficial de DBA Lab, intentar moverse hacia una dirección
 * cuyo bumper sea 'true' provocará una colisión fatal inmediata, terminando la ejecución
 * con control.runStatus = "FAILED".
 */
public class GridBumpers {

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
            return new GridBumpers(false, false, false, false);
        }
        JSONObject val = json.has("value") ? json.getJSONObject("value") : json;
        boolean n = val.optBoolean("north", false);
        boolean e = val.optBoolean("east", false);
        boolean s = val.optBoolean("south", false);
        boolean w = val.optBoolean("west", false);
        return new GridBumpers(n, e, s, w);
    }

    /**
     * Comprueba si una acción física concreta está bloqueada por un muro o límite.
     * 
     * @param action Acción que se desea ejecutar.
     * @return true si la dirección elegida colisionaría; false si está despejada.
     */
    public boolean isBlocked(GridAction action) {
        if (action == null) return true;
        return switch (action) {
            case MOVE_NORTH -> north;
            case MOVE_EAST -> east;
            case MOVE_SOUTH -> south;
            case MOVE_WEST -> west;
        };
    }

    /**
     * Comprueba si una acción física concreta es segura para ser ejecutada.
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
                north ? "BLOQUEADO" : "libre",
                east ? "BLOQUEADO" : "libre",
                south ? "BLOQUEADO" : "libre",
                west ? "BLOQUEADO" : "libre");
    }
}
