package com.mycompany.agentesp2.model.grid;

/**
 * Acciones físicas de movimiento disponibles en el entorno Grid 2D de DBA Lab.
 * 
 * Cada acción define:
 * 1. El código de texto oficial esperado por la API (ej: "grid.move.north").
 * 2. El desplazamiento diferencial en el eje X (dx): columnas hacia la derecha (+1) o izquierda (-1).
 * 3. El desplazamiento diferencial en el eje Y (dy): filas hacia abajo (+1) o arriba (-1).
 * 
 * Recordatorio del sistema de coordenadas de DBA Lab:
 * - (0, 0) está en la esquina superior izquierda.
 * - Eje X (columnas): crece hacia el Este (derecha).
 * - Eje Y (filas): crece hacia el Sur (abajo).
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
     * Devuelve el código literal requerido por la API de DBA Lab.
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
     * Devuelve la acción inversa u opuesta (útil para retrocesos o backtracking).
     */
    public GridAction opposite() {
        return switch (this) {
            case MOVE_NORTH -> MOVE_SOUTH;
            case MOVE_SOUTH -> MOVE_NORTH;
            case MOVE_EAST -> MOVE_WEST;
            case MOVE_WEST -> MOVE_EAST;
        };
    }

    @Override
    public String toString() {
        return actionCode;
    }
}
