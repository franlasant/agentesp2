package com.mycompany.agentesp2.model.grid;

import java.util.Objects;
import org.json.JSONObject;

/**
 * Representa una posición inmutable (x, y) en la cuadrícula del entorno Grid 2D.
 * 
 * Reglas de coordenadas en DBA Lab:
 * - x: Columna desde la izquierda (comenzando en 0).
 * - y: Fila desde la parte superior (comenzando en 0).
 * 
 * Esta clase proporciona utilidades geométricas clave para los algoritmos de navegación,
 * como el cálculo de distancia Manhattan y la obtención de celdas adyacentes según una acción.
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
     * 'grid.position' o 'grid.target'.
     * Soporta tanto el objeto 'value' directo como la respuesta raíz.
     */
    public static GridPosition fromJson(JSONObject json) {
        if (json == null) {
            return new GridPosition(0, 0);
        }
        JSONObject val = json.has("value") ? json.getJSONObject("value") : json;
        int x = val.optInt("x", 0);
        int y = val.optInt("y", 0);
        return new GridPosition(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Calcula la celda vecina resultante de aplicar una acción de movimiento.
     * 
     * @param action Acción a aplicar (NORTH, EAST, SOUTH, WEST).
     * @return Nueva instancia de GridPosition con las coordenadas de la casilla adyacente.
     */
    public GridPosition neighbor(GridAction action) {
        return new GridPosition(this.x + action.getDx(), this.y + action.getDy());
    }

    /**
     * Calcula la distancia Manhattan hasta otra posición destino.
     * En una cuadrícula con movimientos exclusivamente ortogonales (N, S, E, O),
     * la distancia Manhattan representa la cota inferior óptima de pasos sin obstáculos:
     * D = |x1 - x2| + |y1 - y2|
     * 
     * @param other Posición de destino.
     * @return Número mínimo teórico de pasos necesarios para llegar.
     */
    public int manhattanDistance(GridPosition other) {
        if (other == null) return Integer.MAX_VALUE;
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
