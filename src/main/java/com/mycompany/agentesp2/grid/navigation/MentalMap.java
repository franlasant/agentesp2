package com.mycompany.agentesp2.grid.navigation;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * MEMORIA ESPACIAL DEL AGENTE: Mapa Mental Dinámico (MentalMap)
 * ============================================================================
 *
 * Mantiene la representación del entorno descubierta por el agente paso a paso:
 *
 * 1. INDEPENDENCIA DE DIMENSIONES:
 *    No fuerza un tamaño estático fijo (como 10x10): almacena las coordenadas de
 *    forma dispersa (sparse) en un HashMap, permitiendo adaptarse de forma
 *    transparente a mapas pequeños (10x10) o mapas masivos de la Fase 2 (50x50).
 *
 * 2. FUNCIONES DE ACTUALIZACIÓN DEL ESTADO (Diapositiva 33: función 'next'):
 *    Recibe las percepciones sensoriales locales (bumpers y posición) e
 *    incorpora las celdas contiguas como FREE (libres) u OBSTACLE (muros).
 *
 * 3. HIPÓTESIS DEL MUNDO LIBRE (Diapositivas 88-89):
 *    Cualquier celda no percibida aún se clasifica como UNKNOWN (desconocida) y
 *    es considerada provisionalmente transitable por el planificador hasta que
 *    un bumper demuestre lo contrario.
 */
public class MentalMap {

    /** Mapa de coordenadas a tipo de celda conocido (FREE, OBSTACLE, UNKNOWN) */
    private final Map<GridPosition, CellType> cells = new HashMap<>();

    /** Contador de visitas por casilla para el criterio de penalización */
    private final Map<GridPosition, Integer> visitCounts = new HashMap<>();

    /** Límites espaciales observados para ajustar el área de búsqueda y renderizado */
    private int minX = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;

    // =========================================================================
    // 1. MÉTODOS DE ACTUALIZACIÓN COGNITIVA (FUNCIÓN 'next' DEL TEMA 2)
    // =========================================================================

    /**
     * Registra la posición objetivo (target) para incluirla en los límites de cálculo.
     */
    public void registerTarget(GridPosition target) {
        if (target != null) {
            expandBounds(target);
        }
    }

    /**
     * Actualiza el mapa mental con la percepción actual del agente:
     * - La casilla actual se confirma como FREE y se incrementa su contador de visitas.
     * - Las 4 casillas adyacentes se marcan como OBSTACLE (si bumper=true) o FREE (si bumper=false).
     */
    public void updateFromPerceptions(GridPosition current, GridBumpers bumpers) {
        if (current == null) {
            return;
        }

        // Registrar la casilla donde está el agente como libre y visitada
        setCell(current, CellType.FREE);
        visitCounts.put(current, visitCounts.getOrDefault(current, 0) + 1);

        // Actualizar las 4 celdas adyacentes según los sensores de colisión (bumpers)
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
     * Marca manualmente una casilla como obstáculo en caso de detección preventiva.
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

    // =========================================================================
    // 2. MÉTODOS DE CONSULTA Y EVALUACIÓN ESPACIAL
    // =========================================================================

    /**
     * Devuelve el estado conocido de una coordenada (UNKNOWN si aún no ha sido percibida).
     */
    public CellType getCellType(GridPosition pos) {
        return cells.getOrDefault(pos, CellType.UNKNOWN);
    }

    /**
     * Determina si una casilla es transitable bajo la hipótesis optimista:
     * Solo las celdas confirmadas como OBSTACLE son intransitables.
     */
    public boolean isTransitable(GridPosition pos) {
        return getCellType(pos) != CellType.OBSTACLE;
    }

    /**
     * Devuelve el número de veces que el agente ha transitado por una coordenada.
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

    // =========================================================================
    // 3. VISUALIZACIÓN DEL MAPA DESCUBIERTO (RENDERIZADO ASCII)
    // =========================================================================

    /**
     * Dibuja el mapa mental en caracteres ASCII en la consola tras cada paso.
     *
     * Leyenda:
     *   @ : Posición actual de Wall-e (★ si ya ha alcanzado la meta)
     *   $ : Meta (target / EVA)
     *   # : Obstáculo o límite confirmado
     *   + : Casilla libre ya pisada anteriormente
     *   . : Casilla libre observada pero no pisada aún
     *   ? : Terreno desconocido (niebla de guerra)
     */
    public String renderAscii(GridPosition current, GridPosition target) {
        if (current != null) expandBounds(current);
        if (target != null) expandBounds(target);

        int startX = Math.min(0, getMinX());
        int endX = Math.max(9, getMaxX());
        int startY = Math.min(0, getMinY());
        int endY = Math.max(9, getMaxY());

        StringBuilder sb = new StringBuilder();

        // Encabezado de columnas
        sb.append("   ");
        for (int x = startX; x <= endX; x++) {
            sb.append(String.format("%2d ", x));
        }
        sb.append("\n");

        // Filas del mapa
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
