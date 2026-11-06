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
 * Planificador de trayectorias mediante Optimistic Online A* sobre el MentalMap.
 *
 * Características:
 * - Coste de paso g(n): número exacto de movimientos desde la posición actual (1 por paso).
 * - Heurística h(n): distancia Manhattan hasta la meta (admisible y consistente).
 * - Asunción optimista: las celdas FREE y UNKNOWN se consideran transitables; las celdas
 *   OBSTACLE son intransitables. Cuando los sensores revelan nuevos muros que bloquean el camino,
 *   la replanificación desde la celda actual permite rodear barreras cóncavas (U, C, arco)
 *   sin caer en mínimos locales ni bucles.
 */
public class PathPlanner {

    private static final int SEARCH_MARGIN = 25;
    private static final int MAX_EXPANDED_NODES = 10_000;

    private static final class SearchNode implements Comparable<SearchNode> {
        final GridPosition position;
        final SearchNode parent;
        final GridAction actionFromParent;
        final int g;
        final int h;
        final int f;
        final int visits;
        final boolean confirmedFree;

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

        @Override
        public int compareTo(SearchNode other) {
            // 1. Menor coste total f(n) = g(n) + h(n)
            int cmp = Integer.compare(this.f, other.f);
            if (cmp != 0) return cmp;

            // 2. Desempate: menor distancia heurística h(n) a la meta
            cmp = Integer.compare(this.h, other.h);
            if (cmp != 0) return cmp;

            // 3. Desempate: preferir celdas menos visitadas para evitar oscilaciones
            cmp = Integer.compare(this.visits, other.visits);
            if (cmp != 0) return cmp;

            // 4. Desempate: preferir celdas confirmadas FREE frente a UNKNOWN a igualdad total
            return Boolean.compare(other.confirmedFree, this.confirmedFree);
        }
    }

    /**
     * Calcula el camino óptimo de acciones desde 'start' hasta 'target' usando el conocimiento
     * actual del MentalMap.
     *
     * @return Lista ordenada de acciones desde 'start' hasta 'target', o lista vacía si no existe ruta.
     */
    public List<GridAction> findPath(GridPosition start, GridPosition target, MentalMap mentalMap) {
        if (start == null || target == null || mentalMap == null) {
            return Collections.emptyList();
        }
        if (start.equals(target)) {
            return Collections.emptyList();
        }
        if (!mentalMap.isTransitable(target)) {
            return Collections.emptyList();
        }

        int boundMinX = Math.min(Math.min(start.getX(), target.getX()), mentalMap.getMinX()) - SEARCH_MARGIN;
        int boundMaxX = Math.max(Math.max(start.getX(), target.getX()), mentalMap.getMaxX()) + SEARCH_MARGIN;
        int boundMinY = Math.min(Math.min(start.getY(), target.getY()), mentalMap.getMinY()) - SEARCH_MARGIN;
        int boundMaxY = Math.max(Math.max(start.getY(), target.getY()), mentalMap.getMaxY()) + SEARCH_MARGIN;

        PriorityQueue<SearchNode> openSet = new PriorityQueue<>();
        Map<GridPosition, Integer> bestG = new HashMap<>();
        Set<GridPosition> closedSet = new HashSet<>();

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

        int expanded = 0;

        while (!openSet.isEmpty() && expanded < MAX_EXPANDED_NODES) {
            SearchNode current = openSet.poll();

            if (current.position.equals(target)) {
                return reconstructActions(current);
            }

            if (!closedSet.add(current.position)) {
                continue;
            }
            expanded++;

            for (GridAction action : GridAction.values()) {
                GridPosition neighborPos = current.position.neighbor(action);

                if (neighborPos.getX() < boundMinX || neighborPos.getX() > boundMaxX
                        || neighborPos.getY() < boundMinY || neighborPos.getY() > boundMaxY) {
                    continue;
                }

                if (closedSet.contains(neighborPos) || !mentalMap.isTransitable(neighborPos)) {
                    continue;
                }

                int tentativeG = current.g + 1;
                Integer previousBestG = bestG.get(neighborPos);

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

        return Collections.emptyList();
    }

    /**
     * Obtiene la primera acción recomendada para avanzar hacia 'target'.
     * Si no existe camino posible, devuelve Optional.empty() para fallar de forma controlada.
     */
    public Optional<GridAction> findNextAction(GridPosition start, GridPosition target, MentalMap mentalMap) {
        List<GridAction> path = findPath(start, target, mentalMap);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(path.get(0));
    }

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
