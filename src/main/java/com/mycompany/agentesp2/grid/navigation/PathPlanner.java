package com.mycompany.agentesp2.grid.navigation;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * ============================================================================
 * CEREBRO ALGORÍTMICO: Planificador A* Basado en lo Conocido (Known-Space A*)
 * ============================================================================
 *
 * Adaptación del algoritmo A* para mapas masivos con percepción parcial,
 * siguiendo la recomendación del profesor y los principios del Tema 2
 * (Diapositivas 84-91: Búsqueda en Memoria y Exploración por Fronteras):
 *
 * 1. A* ACTUANDO EXCLUSIVAMENTE EN BASE A LO CONOCIDO:
 *    En lugar de planificar en cada turno una ruta ficticia hasta la meta
 *    expandiendo miles de celdas desconocidas (UNKNOWN), ahora A* SOLO expande
 *    celdas confirmadas como libres (CellType.FREE) en el MentalMap.
 *    - Si la meta ya es conocida (FREE), calcula la ruta directa hasta ella.
 *    - Si la meta aún está en la niebla (UNKNOWN), calcula la ruta óptima por
 *      el mapa conocido hasta la mejor celda FRONTERA (celda FREE descubierta
 *      por los bumpers pero aún no visitada) que minimiza f(n) = g(n) + h(n).
 *
 * 2. SIN REPLANIFICACIÓN CONSTANTE (COLA DE PLAN + AVANCE DIRECTO O(1)):
 *    - Persistencia del plan (currentPlan): Cuando A* calcula un camino de
 *      varios pasos por el mapa conocido (por ejemplo, para salir de una trampa
 *      en U o rodear un muro hasta una frontera), guarda toda la secuencia en
 *      currentPlan y la ejecuta paso a paso en O(1) sin volver a invocar A*.
 *    - Avance directo hacia frontera óptima: Si el agente no tiene un plan en
 *      cola y una celda frontera adyacente reduce directamente la distancia
 *      estimada a la meta (alcanzando la cota inferior teórica f = h(start),
 *      g = 1, visits = 0), avanza hacia ella en O(1) sin lanzar A*.
 *
 * 3. HEURÍSTICA QUE TIENE EN CUENTA LOS MUROS CONOCIDOS:
 *    h(n) es la distancia más corta de n a la meta rodeando los muros ya
 *    descubiertos y suponiendo libre lo desconocido. Se calcula con un BFS
 *    desde la meta antes de decidir. La distancia Manhattan ignoraba los muros:
 *    en un embudo (reto 11, barrera triangular) las fronteras del fondo parecían
 *    cercanas a la meta y el agente se quedaba sin energía explorándolas.
 *    Sigue siendo admisible y consistente, porque lo desconocido se supone
 *    libre y nunca sobreestima el camino real.
 *
 * 4. ESCALABILIDAD A MAPAS MASIVOS:
 *    El coste computacional depende únicamente de las celdas descubiertas y no
 *    del tamaño total del mapa (10x10, 500x500 o mayor).
 */
public class PathPlanner {

    /** Cola persistente con las acciones del plan en curso para evitar replanificar en cada paso */
    private final Queue<GridAction> currentPlan = new ArrayDeque<>();

    /** Posición en la que se espera que esté el agente al consumir el siguiente paso del plan */
    private GridPosition expectedPosition = null;

    /** Contador estadístico de cuántas veces se ha invocado la búsqueda A* (findPath) */
    private int replanCount = 0;

    /** Contador estadístico del total de nodos expandidos por A* durante toda la ejecución */
    private int totalExpandedNodes = 0;

    /** Valor de h para celdas desde las que la meta es inalcanzable según lo conocido */
    private static final int UNREACHABLE = Integer.MAX_VALUE / 4;

    /** h(n) de cada celda: distancia BFS hasta la meta rodeando los muros conocidos */
    private final Map<GridPosition, Integer> distanceToTarget = new HashMap<>();

    /**
     * Estructura interna de un nodo del árbol de búsqueda A* sobre el mapa conocido.
     */
    private static final class SearchNode implements Comparable<SearchNode> {
        final GridPosition position;           // Coordenada (x, y) de la casilla conocida
        final SearchNode parent;               // Nodo predecesor en el camino
        final GridAction actionFromParent;     // Acción cardinal que llevó a este nodo
        final int g;                           // Coste real acumulado por el mapa conocido desde start
        final int h;                           // Heurística: distancia BFS hasta la meta rodeando muros conocidos
        final int f;                           // Coste total estimado: f(n) = g(n) + h(n)
        final int visits;                      // Número de veces que el agente ha pisado esta casilla
        final int unknownNeighbors;            // Ganancia de información esperada (vecinos UNKNOWN, Slide 89)
        final long seq;                        // Orden de inserción canónico (N -> E -> S -> W, Slide 49)

        SearchNode(GridPosition position, SearchNode parent, GridAction actionFromParent,
                   int g, int h, int visits, int unknownNeighbors, long seq) {
            this.position = position;
            this.parent = parent;
            this.actionFromParent = actionFromParent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.visits = visits;
            this.unknownNeighbors = unknownNeighbors;
            this.seq = seq;
        }

        /**
         * Ordenación canónica para A* basado en lo conocido (Fronteras - Tema 2, Slides 49 y 89):
         * 1. Menor coste total f(n) = g(n) + h(n) (Criterio combinado: coste conocido + distancia estimada a EVA).
         * 2. Desempate: Menor distancia real g(n) desde el agente (frontera más cercana, evita retrocesos).
         * 3. Desempate: Menor número de visitas previas (prioriza fronteras vírgenes visits=0).
         * 4. Desempate: Mayor información esperada (más vecinos UNKNOWN por descubrir, Slide 89).
         * 5. Desempate: Orden canónico determinista de inserción (Norte -> Este -> Sur -> Oeste, Slide 49).
         */
        @Override
        public int compareTo(SearchNode other) {
            // Criterio 1: Menor coste total estimado f(n) = g(n) + h(n)
            int cmp = Integer.compare(this.f, other.f);
            if (cmp != 0) return cmp;

            // Criterio 2: Menor coste real g(n) (frontera más próxima a la posición actual del agente)
            cmp = Integer.compare(this.g, other.g);
            if (cmp != 0) return cmp;

            // Criterio 3: Menor número de visitas previas
            cmp = Integer.compare(this.visits, other.visits);
            if (cmp != 0) return cmp;

            // Criterio 4: Mayor información esperada (más celdas UNKNOWN adyacentes, Slide 89)
            cmp = Integer.compare(other.unknownNeighbors, this.unknownNeighbors);
            if (cmp != 0) return cmp;

            // Criterio 5: Orden canónico determinista (evita inversión de orden en el heap de PriorityQueue)
            return Long.compare(this.seq, other.seq);
        }
    }

    /**
     * Calcula la ruta óptima de acciones actuando EXCLUSIVAMENTE sobre lo conocido
     * (celdas CellType.FREE en el MentalMap) hasta alcanzar la meta (si ya es conocida)
     * o la mejor celda FRONTERA conocida.
     *
     * @param start     Posición de partida actual del agente
     * @param target    Posición objetivo final (EVA)
     * @param mentalMap Memoria espacial del agente con las celdas conocidas
     * @return Lista ordenada de acciones hasta la frontera/meta, o lista vacía si no hay salida
     */
    public List<GridAction> findPath(GridPosition start, GridPosition target, MentalMap mentalMap) {
        // --- 1. VALIDACIONES INICIALES ---
        if (start == null || target == null || mentalMap == null) {
            return Collections.emptyList();
        }
        if (start.equals(target)) {
            return Collections.emptyList();
        }
        if (!mentalMap.isTransitable(target)) {
            return Collections.emptyList();
        }

        computeDistances(target, mentalMap);
        return searchKnownMap(start, target, mentalMap);
    }

    /**
     * Búsqueda A* sobre las celdas FREE conocidas. Requiere que distanceToTarget esté calculado.
     */
    private List<GridAction> searchKnownMap(GridPosition start, GridPosition target, MentalMap mentalMap) {
        replanCount++;

        // --- 2. ESTRUCTURAS DE DATOS DE BÚSQUEDA A* EN EL GRAFO CONOCIDO ---
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>();
        Set<GridPosition> closedSet = new HashSet<>();
        Map<GridPosition, Integer> bestG = new HashMap<>();

        long seqCounter = 0;
        SearchNode root = new SearchNode(
                start,
                null,
                null,
                0,
                h(start),
                mentalMap.getVisitCount(start),
                mentalMap.countUnknownNeighbors(start),
                seqCounter++
        );
        openSet.add(root);
        bestG.put(start, 0);

        // --- 3. BUCLE DE BÚSQUEDA EXCLUSIVAMENTE SOBRE CELDAS CONOCIDAS (FREE) ---
        while (!openSet.isEmpty()) {
            SearchNode current = openSet.poll();

            // Condición de parada en base a lo conocido:
            // - Hemos llegado a la meta G, o
            // - Hemos llegado a la mejor celda FRONTERA conocida (FREE, no visitada y junto a terreno UNKNOWN).
            //   Las fronteras desde las que la meta es inalcanzable no cuentan: explorarlas no acerca a nada.
            if (!current.position.equals(start)
                    && (current.position.equals(target)
                        || (mentalMap.isFrontier(current.position) && current.h < UNREACHABLE))) {
                return reconstructActions(current);
            }

            if (!closedSet.add(current.position)) {
                continue;
            }
            totalExpandedNodes++;

            // Expandir únicamente vecinos cardinales que YA sean conocidos como FREE
            for (GridAction action : GridAction.values()) {
                GridPosition neighborPos = current.position.neighbor(action);

                if (closedSet.contains(neighborPos) || !mentalMap.isKnownFree(neighborPos)) {
                    continue;
                }

                int tentativeG = current.g + 1;
                Integer previousBestG = bestG.get(neighborPos);

                if (previousBestG == null || tentativeG < previousBestG) {
                    bestG.put(neighborPos, tentativeG);
                    int h = h(neighborPos);
                    int visits = mentalMap.getVisitCount(neighborPos);
                    int unknownNeighbors = mentalMap.countUnknownNeighbors(neighborPos);

                    SearchNode neighborNode = new SearchNode(
                            neighborPos, current, action, tentativeG, h, visits, unknownNeighbors, seqCounter++
                    );
                    openSet.add(neighborNode);
                }
            }
        }

        // Si se agotan las celdas FREE conocidas sin encontrar ninguna frontera ni la meta, no existe salida
        return Collections.emptyList();
    }

    /**
     * Decide la siguiente acción sin replanificar innecesariamente:
     * 1. Si hay un plan activo en cola (currentPlan) sobre celdas FREE conocidas, consume el siguiente paso en O(1).
     * 2. Si hay una celda frontera adyacente que acerca directamente a la meta (cota mínima f = h(start), g = 1),
     *    avanza directamente hacia ella en O(1) sin necesidad de lanzar A*.
     * 3. Solo cuando un obstáculo impide avanzar directamente, ejecuta A* sobre el mapa conocido (MentalMap)
     *    para calcular la ruta hasta la mejor frontera disponible y la almacena en currentPlan.
     */
    public Optional<GridAction> findNextAction(GridPosition start, GridPosition target, MentalMap mentalMap) {
        if (start == null || target == null || mentalMap == null || start.equals(target)) {
            currentPlan.clear();
            return Optional.empty();
        }

        // 1. Reutilizar el plan activo si sigue siendo válido
        if (!currentPlan.isEmpty() && start.equals(expectedPosition)) {
            GridAction nextAction = currentPlan.peek();
            GridPosition nextPos = start.neighbor(nextAction);
            if (mentalMap.isKnownFree(nextPos)) {
                currentPlan.poll();
                expectedPosition = nextPos;
                return Optional.of(nextAction);
            }
            currentPlan.clear();
        } else {
            currentPlan.clear();
        }

        // Sin plan válido: recalcular h con los muros descubiertos hasta ahora
        computeDistances(target, mentalMap);

        // 2. Avance directo en O(1) si existe una frontera inmediata que reduce la distancia estimada a la meta
        GridAction directAction = findDirectForwardFrontier(start, target, mentalMap);
        if (directAction != null) {
            expectedPosition = start.neighbor(directAction);
            return Optional.of(directAction);
        }

        // 3. Planificar con A* sobre el mapa conocido hasta la mejor frontera (o hasta la meta)
        List<GridAction> newPlan = mentalMap.isTransitable(target)
                ? searchKnownMap(start, target, mentalMap)
                : Collections.emptyList();
        if (newPlan.isEmpty()) {
            expectedPosition = null;
            return Optional.empty();
        }

        currentPlan.addAll(newPlan);
        GridAction firstAction = currentPlan.poll();
        expectedPosition = start.neighbor(firstAction);
        return Optional.of(firstAction);
    }

    /**
     * Comprueba en O(1) si alguno de los 4 vecinos cardinales conocidos como FREE es la meta o una frontera
     * no visitada que reduce directamente la distancia estimada h(n) = h(start) - 1.
     * Por consistencia de la heurística, dicho vecino alcanza el mínimo global teórico f = h(start) y g = 1.
     */
    private GridAction findDirectForwardFrontier(GridPosition start, GridPosition target, MentalMap mentalMap) {
        if (!mentalMap.isTransitable(target)) {
            return null;
        }
        int currentH = h(start);
        GridAction bestAction = null;
        int bestUnknownNeighbors = -1;

        for (GridAction action : GridAction.values()) {
            GridPosition neighbor = start.neighbor(action);
            if (!mentalMap.isKnownFree(neighbor)) {
                continue;
            }
            if (neighbor.equals(target)) {
                return action;
            }
            if (h(neighbor) < currentH && mentalMap.isFrontier(neighbor)) {
                int unknown = mentalMap.countUnknownNeighbors(neighbor);
                if (unknown > bestUnknownNeighbors) {
                    bestUnknownNeighbors = unknown;
                    bestAction = action;
                }
            }
        }
        return bestAction;
    }

    /**
     * Calcula h(n) para todas las celdas: BFS desde la meta que atraviesa cualquier celda que no sea un muro
     * conocido (lo desconocido se supone libre). Se limita al rectángulo de lo conocido más un margen de una
     * casilla, suficiente para rodear cualquier muro descubierto.
     */
    private void computeDistances(GridPosition target, MentalMap mentalMap) {
        distanceToTarget.clear();
        int minX = Math.min(mentalMap.getMinX(), target.getX()) - 1;
        int maxX = Math.max(mentalMap.getMaxX(), target.getX()) + 1;
        int minY = Math.min(mentalMap.getMinY(), target.getY()) - 1;
        int maxY = Math.max(mentalMap.getMaxY(), target.getY()) + 1;

        Queue<GridPosition> queue = new ArrayDeque<>();
        distanceToTarget.put(target, 0);
        queue.add(target);
        while (!queue.isEmpty()) {
            GridPosition current = queue.poll();
            int next = distanceToTarget.get(current) + 1;
            for (GridAction action : GridAction.values()) {
                GridPosition neighbor = current.neighbor(action);
                if (neighbor.getX() < minX || neighbor.getX() > maxX
                        || neighbor.getY() < minY || neighbor.getY() > maxY) {
                    continue;
                }
                if (distanceToTarget.containsKey(neighbor) || !mentalMap.isTransitable(neighbor)) {
                    continue;
                }
                distanceToTarget.put(neighbor, next);
                queue.add(neighbor);
            }
        }
    }

    /** Heurística h(n): distancia BFS a la meta, o UNREACHABLE si los muros conocidos la aíslan */
    private int h(GridPosition position) {
        return distanceToTarget.getOrDefault(position, UNREACHABLE);
    }

    /**
     * Invalida manualmente el plan en caché para forzar una replanificación limpia.
     */
    public void clearPlan() {
        currentPlan.clear();
        expectedPosition = null;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public int getTotalExpandedNodes() {
        return totalExpandedNodes;
    }

    /**
     * Devuelve una copia inmutable de las acciones que quedan actualmente en la cola del plan.
     */
    public List<GridAction> getCurrentPlanSnapshot() {
        return new ArrayList<>(currentPlan);
    }

    /**
     * Reconstruye la secuencia ordenada de acciones desde start hasta la frontera/meta elegida.
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
