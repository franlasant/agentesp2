package com.mycompany.agentesp2.grid.navigation;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapa mental dinámico construido por el agente a partir de sus percepciones locales.
 *
 * No asume un tamaño de mundo prefijado (no fuerza 10x10): almacena las celdas de forma
 * dispersa en memoria, adaptándose a cualquier dimensión o coordenada en la Tanda 1 y Tanda 2.
 */
public class MentalMap {

    private final Map<GridPosition, CellType> cells = new HashMap<>();
    private final Map<GridPosition, Integer> visitCounts = new HashMap<>();

    private int minX = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;

    /**
     * Registra la posición objetivo para que quede incluida dentro del área de visualización y búsqueda.
     */
    public void registerTarget(GridPosition target) {
        if (target != null) {
            expandBounds(target);
        }
    }

    /**
     * Actualiza el mapa mental con la posición actual del agente y la lectura de sus 4 bumpers.
     * - La posición actual se marca como FREE y se incrementa su contador de visitas.
     * - Cada casilla adyacente con bumper == true se marca como OBSTACLE (muro o borde del mundo).
     * - Cada casilla adyacente con bumper == false se marca como FREE.
     */
    public void updateFromPerceptions(GridPosition current, GridBumpers bumpers) {
        if (current == null) {
            return;
        }
        setCell(current, CellType.FREE);
        visitCounts.put(current, visitCounts.getOrDefault(current, 0) + 1);

        if (bumpers != null) {
            for (GridAction action : GridAction.values()) {
                GridPosition neighbor = current.neighbor(action);
                if (bumpers.isBlocked(action)) {
                    setCell(neighbor, CellType.OBSTACLE);
                } else {
                    setCell(neighbor, CellType.FREE);
                }
            }
        }
    }

    /**
     * Marca explícitamente una posición como obstáculo (por ejemplo, si una validación previa
     * detecta un bloqueo inesperado).
     */
    public void markObstacle(GridPosition pos) {
        if (pos != null) {
            setCell(pos, CellType.OBSTACLE);
        }
    }

    private void setCell(GridPosition pos, CellType type) {
        cells.put(pos, type);
        expandBounds(pos);
    }

    private void expandBounds(GridPosition pos) {
        if (pos.getX() < minX) minX = pos.getX();
        if (pos.getX() > maxX) maxX = pos.getX();
        if (pos.getY() < minY) minY = pos.getY();
        if (pos.getY() > maxY) maxY = pos.getY();
    }

    /**
     * Devuelve el estado conocido de una coordenada (UNKNOWN por defecto si aún no se ha percibido).
     */
    public CellType getCellType(GridPosition pos) {
        return cells.getOrDefault(pos, CellType.UNKNOWN);
    }

    /**
     * Bajo la estrategia Optimistic Online A*, una celda es transitable si es FREE o UNKNOWN.
     * Solo las celdas confirmadas como OBSTACLE son intransitables.
     */
    public boolean isTransitable(GridPosition pos) {
        return getCellType(pos) != CellType.OBSTACLE;
    }

    /**
     * Devuelve cuántas veces ha pisado el agente una casilla concreta.
     */
    public int getVisitCount(GridPosition pos) {
        return visitCounts.getOrDefault(pos, 0);
    }

    public int getMinX() {
        return minX == Integer.MAX_VALUE ? 0 : minX;
    }

    public int getMaxX() {
        return maxX == Integer.MIN_VALUE ? 0 : maxX;
    }

    public int getMinY() {
        return minY == Integer.MAX_VALUE ? 0 : minY;
    }

    public int getMaxY() {
        return maxY == Integer.MIN_VALUE ? 0 : maxY;
    }

    public Map<GridPosition, CellType> getKnownCells() {
        return Collections.unmodifiableMap(cells);
    }

    /**
     * Renderiza el mapa mental descubierto en formato ASCII para mostrar en consola tras cada paso.
     *
     * Leyenda:
     *   @ : Posición actual del agente (★ si ya está sobre la meta)
     *   $ : Meta (target)
     *   # : Obstáculo o límite del mundo (OBSTACLE)
     *   + : Casilla libre ya visitada por el agente
     *   . : Casilla libre observada por sensores pero aún no pisada
     *   ? : Casilla desconocida (UNKNOWN)
     */
    public String renderAscii(GridPosition current, GridPosition target) {
        if (current != null) expandBounds(current);
        if (target != null) expandBounds(target);

        int startX = Math.min(0, getMinX());
        int endX = Math.max(9, getMaxX());
        int startY = Math.min(0, getMinY());
        int endY = Math.max(9, getMaxY());

        StringBuilder sb = new StringBuilder();
        sb.append("   ");
        for (int x = startX; x <= endX; x++) {
            sb.append(String.format("%2d ", x));
        }
        sb.append("\n");

        for (int y = startY; y <= endY; y++) {
            sb.append(String.format("%2d ", y));
            for (int x = startX; x <= endX; x++) {
                GridPosition pos = new GridPosition(x, y);
                char ch;
                if (pos.equals(current) && pos.equals(target)) {
                    ch = '★';
                } else if (pos.equals(current)) {
                    ch = '@';
                } else if (pos.equals(target)) {
                    ch = '$';
                } else {
                    CellType type = getCellType(pos);
                    if (type == CellType.OBSTACLE) {
                        ch = '#';
                    } else if (type == CellType.FREE) {
                        ch = getVisitCount(pos) > 0 ? '+' : '.';
                    } else {
                        ch = '?';
                    }
                }
                sb.append(" ").append(ch).append(" ");
            }
            sb.append("\n");
        }
        sb.append("Leyenda: [@] Agente | [$] Meta | [#] Muro/Borde | [+] Pisada | [.] Libre | [?] Desconocida");
        return sb.toString();
    }
}
