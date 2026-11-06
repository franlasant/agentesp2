package com.mycompany.agentesp2.model.grid;

/**
 * Acciones físicas de movimiento disponibles en el entorno Grid 2D de DBA Lab.
 *
 * Sistema de coordenadas oficial de DBA Lab:
 * - (0, 0) es la esquina superior izquierda.
 * - Eje X (columnas): crece hacia el Este (derecha, dx = +1) y decrece hacia el Oeste (izquierda, dx = -1).
 * - Eje Y (filas): crece hacia el Sur (abajo, dy = +1) y decrece hacia el Norte (arriba, dy = -1).
 */
public enum GridAction {

    MOVE_NORTH("grid.move.north", 0, -1),
    MOVE_EAST("grid.move.east", 1, 0),
    MOVE_SOUTH("grid.move.south", 0, 1),
    MOVE_WEST("grid.move.west", -1, 0);

    private final String actionCode;
    private final int dx;
    private final int dy;

    GridAction(String actionCode, int dx, int dy) {
        this.actionCode = actionCode;
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * Devuelve el código literal requerido por la API de DBA Lab (ej. "grid.move.north").
     */
    public String getActionCode() {
        return actionCode;
    }

    /**
     * Desplazamiento en el eje X (columnas).
     * @return +1 para Este, -1 para Oeste, 0 para Norte/Sur.
     */
    public int getDx() {
        return dx;
    }

    /**
     * Desplazamiento en el eje Y (filas).
     * @return +1 para Sur, -1 para Norte, 0 para Este/Oeste.
     */
    public int getDy() {
        return dy;
    }

    /**
     * Devuelve la acción cardinal opuesta (útil para retrocesos o geometría).
     */
    public GridAction opposite() {
        return switch (this) {
            case MOVE_NORTH -> MOVE_SOUTH;
            case MOVE_SOUTH -> MOVE_NORTH;
            case MOVE_EAST -> MOVE_WEST;
            case MOVE_WEST -> MOVE_EAST;
        };
    }

    /**
     * Busca la acción correspondiente a un código de texto de la API.
     */
    public static GridAction fromActionCode(String code) {
        if (code == null) {
            return null;
        }
        for (GridAction action : values()) {
            if (action.actionCode.equalsIgnoreCase(code.trim())) {
                return action;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return actionCode;
    }
}
