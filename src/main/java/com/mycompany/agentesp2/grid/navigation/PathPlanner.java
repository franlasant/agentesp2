package com.mycompany.agentesp2.grid.navigation;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * ============================================================================
 * CEREBRO ALGORÍTMICO: Planificador de Trayectorias (Optimistic Online A*)
 * ============================================================================
 *
 * Implementa el algoritmo de búsqueda heurística en línea basado en los
 * principios del Tema 2 de la asignatura (Diseño Basado en Agentes - UGR):
 *
 * 1. CÁLCULO MENTAL EN GRAFO (Diapositivas 84 y 91):
 *    Planifica la ruta óptima en memoria interna antes de realizar movimientos
 *    físicos, evitando el coste del retroceso físico (backtracking) de DFS.
 *
 * 2. HEURÍSTICA MANHATTAN ADMISIBLE (Diapositivas 44 y 52):
 *    Al permitir únicamente 4 movimientos cardinales con coste unitario:
 *    h(s) = |x - x_G| + |y - y_G|
 *    Garantiza que nunca sobreestima el coste restante real.
 *
 * 3. HIPÓTESIS DEL MUNDO LIBRE / EXPLORACIÓN POR FRONTERAS (Diapositivas 88-89):
 *    Las celdas desconocidas (UNKNOWN) se consideran provisionalmente transitables.
 *    Si un sensor bumper detecta un muro imprevisto, se actualiza el mapa mental
 *    y se replanifica instantáneamente en memoria en 0 pasos físicos.
 *
 * 4. PENALIZACIÓN DE VISITAS EN DESEMPATES (Diapositivas 56-59):
 *    En caso de empate en coste f(n) y heurística h(n), se favorece la casilla
 *    con menor número de visitas previas, rompiendo posibles bucles de oscilación.
 */
public class PathPlanner {

    /** Margen de expansión en casillas alrededor del área explorada */
    private static final int SEARCH_MARGIN = 25;

    /** Límite de seguridad para evitar bloqueos por recursión en mapas masivos */
    private static final int MAX_EXPANDED_NODES = 10_000;

    /**
     * Estructura interna de un nodo del árbol de búsqueda A*.
     */
    private static final class SearchNode implements Comparable<SearchNode> {
        final GridPosition position;           // Coordenada (x, y) de la casilla
        final SearchNode parent;               // Nodo predecesor en el camino
        final GridAction actionFromParent;     // Acción cardinal que llevó a este nodo
        final int g;                           // Coste real acumulado desde el inicio (1 paso = 1 de coste)
        final int h;                           // Heurística Manhattan estimada hasta la meta
        final int f;                           // Coste total estimado: f(n) = g(n) + h(n)
        final int visits;                      // Número de veces que el agente ha pisado esta casilla
        final boolean confirmedFree;           // true si ya ha sido confirmada como libre por sensores

        SearchNode(GridPosition position, SearchNode parent, GridAction actionFromParent,
                   int g, int h, int visits, boolean confirmedFree) {
            this.position = position;
            this.parent = parent;
            this.actionFromParent = actionFromParent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.visits = visits;
            this.confirmedFree = confirmedFree;
        }

        /**
         * Escalera canónica de prioridades para la cola de prioridad (Lista Abierta):
         * 1. Menor coste total f(n) = g(n) + h(n)
         * 2. Desempate: Menor heurística h(n) (más cercano a la meta)
         * 3. Desempate: Menor número de visitas previas (penalización para romper bucles)
         * 4. Desempate: Preferir celdas confirmadas frente a desconocidas
         */
        @Override
        public int compareTo(SearchNode other) {
            // Criterio 1: Menor coste f
            int cmp = Integer.compare(this.f, other.f);
            if (cmp != 0) return cmp;

            // Criterio 2: Menor distancia estimada a la meta (h)
            cmp = Integer.compare(this.h, other.h);
            if (cmp != 0) return cmp;

            // Criterio 3: Penalización de visitas de clase (Tema 2 Slide 56)
            cmp = Integer.compare(this.visits, other.visits);
            if (cmp != 0) return cmp;

            // Criterio 4: Certeza sensorial (FREE confirmada antes que UNKNOWN)
            return Boolean.compare(other.confirmedFree, this.confirmedFree);
        }
    }

    /**
     * Calcula la ruta óptima de acciones desde la posición actual hasta la meta
     * utilizando el conocimiento consolidado en el MentalMap.
     *
     * @param start     Posición de partida del agente
     * @param target    Posición objetivo a alcanzar
     * @param mentalMap Memoria espacial del agente con celdas conocidas
     * @return Lista ordenada de acciones a ejecutar, o lista vacía si no existe ruta alcanzable
     */
    public List<GridAction> findPath(GridPosition start, GridPosition target, MentalMap mentalMap) {
        // --- 1. VALIDACIONES INICIALES ---
        if (start == null || target == null || mentalMap == null) {
            return Collections.emptyList();
        }
        if (start.equals(target)) {
            return Collections.emptyList(); // Ya está en la meta
        }
        if (!mentalMap.isTransitable(target)) {
            return Collections.emptyList(); // La meta está confirmada como obstáculo
        }

        // --- 2. LÍMITES DINÁMICOS DE BÚSQUEDA ---
        int boundMinX = Math.min(Math.min(start.getX(), target.getX()), mentalMap.getMinX()) - SEARCH_MARGIN;
        int boundMaxX = Math.max(Math.max(start.getX(), target.getX()), mentalMap.getMaxX()) + SEARCH_MARGIN;
        int boundMinY = Math.min(Math.min(start.getY(), target.getY()), mentalMap.getMinY()) - SEARCH_MARGIN;
        int boundMaxY = Math.max(Math.max(start.getY(), target.getY()), mentalMap.getMaxY()) + SEARCH_MARGIN;

        // --- 3. ESTRUCTURAS DE DATOS DE BÚSQUEDA A* ---
        // Lista Abierta: nodos frontera ordenados por compareTo (f, h, visitas)
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>();

        // Lista Cerrada: celdas cuyas mejores rutas ya han sido exploradas
        Set<GridPosition> closedSet = new HashSet<>();

        // Registro del menor coste g(n) alcanzado por coordenada
        Map<GridPosition, Integer> bestG = new HashMap<>();

        // Nodo raíz inicial
        SearchNode root = new SearchNode(
                start,
                null,
                null,
                0,
                start.manhattanDistance(target),
                mentalMap.getVisitCount(start),
                true
        );
        openSet.add(root);
        bestG.put(start, 0);

        int nodesExpanded = 0;

        // --- 4. BUCLE PRINCIPAL DE EXPLORACIÓN ---
        while (!openSet.isEmpty() && nodesExpanded < MAX_EXPANDED_NODES) {
            SearchNode current = openSet.poll();

            // ¿Hemos alcanzado el objetivo? Reconstruir y devolver ruta
            if (current.position.equals(target)) {
                return reconstructActions(current);
            }

            // Si la celda ya fue cerrada con mejor coste, ignorar duplicados
            if (!closedSet.add(current.position)) {
                continue;
            }
            nodesExpanded++;

            // --- 5. EVALUACIÓN DE VECINOS CARDINALES (N, E, S, W) ---
            for (GridAction action : GridAction.values()) {
                GridPosition neighborPos = current.position.neighbor(action);

                // Descartar si excede los márgenes de seguridad del mapa
                if (neighborPos.getX() < boundMinX || neighborPos.getX() > boundMaxX
                        || neighborPos.getY() < boundMinY || neighborPos.getY() > boundMaxY) {
                    continue;
                }

                // Descartar si ya fue explorado o está confirmado como obstáculo
                if (closedSet.contains(neighborPos) || !mentalMap.isTransitable(neighborPos)) {
                    continue;
                }

                int tentativeG = current.g + 1;
                Integer previousBestG = bestG.get(neighborPos);

                // Si encontramos un camino más corto hacia este vecino, actualizar
                if (previousBestG == null || tentativeG < previousBestG) {
                    bestG.put(neighborPos, tentativeG);
                    int h = neighborPos.manhattanDistance(target);
                    int visits = mentalMap.getVisitCount(neighborPos);
                    boolean isFree = mentalMap.getCellType(neighborPos) == CellType.FREE;

                    SearchNode neighborNode = new SearchNode(
                            neighborPos, current, action, tentativeG, h, visits, isFree
                    );
                    openSet.add(neighborNode);
                }
            }
        }

        // Si se vacía la cola sin llegar a la meta, no existe ruta transitable conocida
        return Collections.emptyList();
    }

    /**
     * Obtiene únicamente la primera acción inmediata que el agente debe ejecutar.
     * Si no existe camino posible, devuelve Optional.empty() para permitir una
     * detención segura y controlada sin chocar contra muros.
     */
    public Optional<GridAction> findNextAction(GridPosition start, GridPosition target, MentalMap mentalMap) {
        List<GridAction> path = findPath(start, target, mentalMap);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(path.get(0));
    }

    /**
     * Reconstruye la secuencia ordenada de acciones desde el nodo inicial hasta la meta
     * siguiendo la cadena de punteros 'parent'.
     */
    private List<GridAction> reconstructActions(SearchNode goalNode) {
        List<GridAction> actions = new ArrayList<>();
        SearchNode curr = goalNode;
        while (curr != null && curr.actionFromParent != null) {
            actions.add(curr.actionFromParent);
            curr = curr.parent;
        }
        Collections.reverse(actions);
        return actions;
    }
}
