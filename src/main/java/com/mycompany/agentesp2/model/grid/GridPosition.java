package com.mycompany.agentesp2.model.grid;

import java.util.Objects;
import org.json.JSONObject;

/**
 * Representa una posición inmutable (x, y) en la cuadrícula del entorno Grid 2D.
 *
 * Reglas de coordenadas en DBA Lab:
 * - x: Columna desde la izquierda (Norte/Sur mantiene x; Este = x+1; Oeste = x-1).
 * - y: Fila desde arriba (Este/Oeste mantiene y; Norte = y-1; Sur = y+1).
 */
public final class GridPosition {

    private final int x;
    private final int y;

    public GridPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Construye una instancia a partir del JSON devuelto por los sensores
     * 'grid.position' o 'grid.target'. Soporta tanto el objeto raíz (con bloque "value")
     * como el objeto "value" directo.
     */
    public static GridPosition fromJson(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("El JSON de posición no puede ser nulo.");
        }
        JSONObject val = json.has("value") ? json.getJSONObject("value") : json;
        int x = val.getInt("x");
        int y = val.getInt("y");
        return new GridPosition(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Calcula la celda vecina resultante de aplicar una acción de movimiento cardinal.
     */
    public GridPosition neighbor(GridAction action) {
        Objects.requireNonNull(action, "La acción no puede ser nula.");
        return new GridPosition(this.x + action.getDx(), this.y + action.getDy());
    }

    /**
     * Determina qué acción cardinal lleva desde esta posición hasta una celda adyacente.
     * Devuelve null si 'targetNeighbor' no es ortogonalmente adyacente a distancia 1.
     */
    public GridAction actionTowards(GridPosition targetNeighbor) {
        if (targetNeighbor == null) {
            return null;
        }
        int dx = targetNeighbor.x - this.x;
        int dy = targetNeighbor.y - this.y;
        for (GridAction action : GridAction.values()) {
            if (action.getDx() == dx && action.getDy() == dy) {
                return action;
            }
        }
        return null;
    }

    /**
     * Calcula la distancia Manhattan hasta otra posición: |x1 - x2| + |y1 - y2|.
     * Es una heurística admisible y consistente en cuadrículas con movimientos de 4 direcciones.
     */
    public int manhattanDistance(GridPosition other) {
        if (other == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GridPosition that = (GridPosition) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("(%d, %d)", x, y);
    }
}
