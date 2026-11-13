package com.mycompany.agentesp2.grid.navigation;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ============================================================================
 * CEREBRO ALGORÍTMICO: Navegador Autónomo Bug2 (Seguimiento de Muros)
 * ============================================================================
 *
 * Implementa el algoritmo canónico Bug2 para navegación en cuadrícula 2D según
 * la especificación formal del Tema 2 de la asignatura (Diseño Basado en Agentes - UGR).
 *
 * PRINCIPIOS DE DISEÑO (Clean Code):
 * 1. Desacoplamiento total: No realiza peticiones de red ni parseos de JSON.
 *    Opera exclusivamente sobre los DTOs inmutables: GridPosition, GridBumpers y GridAction.
 * 2. Máquina de estados finita:
 *    - MOTION_TO_GOAL: Desplazamiento directo por heurística Manhattan hacia la meta.
 *    - WALL_FOLLOWING: Contorneo del contorno mediante regla de mano derecha (wallFollowDecision).
 * 3. Línea m-line discreta 4-conexa:
 *    Garantiza que cualquier trayectoria ortogonal que cruce la recta S -> G sea detectada.
 * 4. Condición matemática de abandono (Leave Point L_i):
 *    - El agente pisa una celda de la m-line.
 *    - La distancia actual a la meta es estrictamente menor que en el punto de contacto:
 *      d(L_i, G) < d(H_i, G).
 *    - La acción directa hacia la meta está libre según los sensores bumpers.
 * 5. Detección formal de metas inalcanzables:
 *    Si el agente completa una vuelta al obstáculo regresando a H_i sin encontrar salida,
 *    se detiene de forma controlada (Optional.empty()).
 */
public class Bug2Navigator {

    public enum Mode {
        MOTION_TO_GOAL,
        WALL_FOLLOWING
    }

    private final GridPosition start;
    private final GridPosition goal;

    private boolean followingWall = false;
    private int distanceWhenWallStarted = Integer.MAX_VALUE;
    private GridAction currentDir = GridAction.MOVE_NORTH;
    private GridPosition hitPoint = null;
    private int wallSteps = 0;
    private final Set<GridPosition> visitedWall = new HashSet<>();

    public Bug2Navigator(GridPosition start, GridPosition goal) {
        this.start = Objects.requireNonNull(start, "La posición inicial (start) no puede ser nula.");
        this.goal = Objects.requireNonNull(goal, "La posición objetivo (goal) no puede ser nula.");

        // Orientación inicial hacia la meta
        GridAction initial = getBestActionTowardsGoal(start);
        if (initial != null) {
            this.currentDir = initial;
        }
    }

    public GridPosition getStart() {
        return start;
    }

    public GridPosition getGoal() {
        return goal;
    }

    public boolean isFollowingWall() {
        return followingWall;
    }

    public Mode getCurrentMode() {
        return followingWall ? Mode.WALL_FOLLOWING : Mode.MOTION_TO_GOAL;
    }

    public GridPosition getHitPoint() {
        return hitPoint;
    }

    public int getDistanceWhenWallStarted() {
        return distanceWhenWallStarted;
    }

    /**
     * Calcula la siguiente acción de movimiento a partir de la posición actual y los bumpers.
     *
     * @param current Posición actual del agente.
     * @param bumpers Percepción de proximidad (norte, este, sur, oeste).
     * @return Siguiente acción cardinal validada, o Optional.empty() si se alcanzó la meta
     *         o si se detectó una meta inalcanzable (parada controlada).
     */
    public Optional<GridAction> nextAction(GridPosition current, GridBumpers bumpers) {
        if (current == null || current.equals(goal)) {
            return Optional.empty();
        }

        int distToGoal = current.manhattanDistance(goal);

        // =====================================================================
        // 1. MODO SEGUIMIENTO DE MURO (WALL_FOLLOWING)
        // =====================================================================
        if (followingWall) {
            boolean onMLine = isOnMLine(current);
            GridAction directToGoal = getBestActionTowardsGoal(current);
            boolean directSafe = directToGoal != null && bumpers.isSafe(directToGoal);

            // Condición de abandono de muro (Leave Point L_i):
            // - Sobre la m-line con progreso y paso a meta despejado
            // - O progreso estricto respecto al choque con paso a meta despejado
            if ((onMLine && distToGoal < distanceWhenWallStarted && directSafe && wallSteps > 1)
                    || (distToGoal < distanceWhenWallStarted && directSafe && wallSteps > 1)) {
                followingWall = false;
                hitPoint = null;
                distanceWhenWallStarted = Integer.MAX_VALUE;
                wallSteps = 0;
                visitedWall.clear();
                currentDir = directToGoal;
                return Optional.of(directToGoal);
            }

            // Detección de bucle infinito (meta inalcanzable cerrada por muros):
            // Si regresa al punto de impacto H_i habiendo dado una vuelta completa
            if (current.equals(hitPoint) && wallSteps > 4) {
                return Optional.empty();
            }

            GridAction wallAction = wallFollowDecision(bumpers);
            if (wallAction != null) {
                wallSteps++;
                visitedWall.add(current);
                return Optional.of(wallAction);
            } else {
                followingWall = false;
                wallSteps = 0;
            }
        }

        // =====================================================================
        // 2. MODO ACERCAMIENTO A LA META (MOTION_TO_GOAL)
        // =====================================================================
        GridAction heuristicAction = getBestActionTowardsGoal(current);
        if (heuristicAction == null) {
            return Optional.empty();
        }

        boolean validMove = bumpers.isSafe(heuristicAction);

        // Si nos topamos con un muro: entrar en WALL_FOLLOWING
        if (!validMove) {
            currentDir = heuristicAction;
            GridAction resultado = wallFollowDecision(bumpers);
            followingWall = true;
            distanceWhenWallStarted = distToGoal;
            hitPoint = current;
            wallSteps = 1;
            visitedWall.clear();
            visitedWall.add(current);
            return Optional.ofNullable(resultado);
        }

        currentDir = heuristicAction;
        return Optional.of(heuristicAction);
    }

    /**
     * Selecciona la acción que más reduce la distancia Manhattan hacia la meta.
     */
    private GridAction getBestActionTowardsGoal(GridPosition current) {
        List<GridAction> possible = new ArrayList<>(List.of(GridAction.values()));
        possible.sort(Comparator.comparingInt(a -> current.neighbor(a).manhattanDistance(goal)));
        return possible.get(0);
    }

    /**
     * Cinemática de contorneo de pared por mano derecha (inspirada en la solución evaluada de DBA).
     *
     * 1. Si el avance actual es válido y a la derecha hay muro, continúa recto.
     * 2. Si a la derecha no hay muro, gira a la derecha para seguir abrazándolo.
     * 3. Si el frente está bloqueado por el muro:
     *    - Si ya seguíamos el muro y la derecha está libre, gira a la derecha.
     *    - Si la izquierda está bloqueada, rota y reevalúa.
     *    - Si la izquierda está libre, gira a la izquierda para dejar el obstáculo a la derecha.
     */
    private GridAction wallFollowDecision(GridBumpers bumpers) {
        boolean validMove = bumpers.isSafe(currentDir);
        GridAction turnRight = turnRight(currentDir);
        GridAction turnLeft = turnLeft(currentDir);

        if (validMove) {
            if (bumpers.isBlocked(turnRight)) {
                return currentDir;
            } else {
                currentDir = turnRight;
                return turnRight;
            }
        } else {
            if (followingWall && bumpers.isSafe(turnRight)) {
                currentDir = turnRight;
                return turnRight;
            }
            if (bumpers.isBlocked(turnLeft)) {
                currentDir = turnLeft;
                return wallFollowDecision(bumpers);
            } else {
                currentDir = turnLeft;
                return turnLeft;
            }
        }
    }

    /**
     * Giro de 90 grados en sentido horario.
     */
    public static GridAction turnRight(GridAction a) {
        return switch (a) {
            case MOVE_NORTH -> GridAction.MOVE_EAST;
            case MOVE_EAST -> GridAction.MOVE_SOUTH;
            case MOVE_SOUTH -> GridAction.MOVE_WEST;
            case MOVE_WEST -> GridAction.MOVE_NORTH;
        };
    }

    /**
     * Giro de 90 grados en sentido antihorario.
     */
    public static GridAction turnLeft(GridAction a) {
        return switch (a) {
            case MOVE_NORTH -> GridAction.MOVE_WEST;
            case MOVE_WEST -> GridAction.MOVE_SOUTH;
            case MOVE_SOUTH -> GridAction.MOVE_EAST;
            case MOVE_EAST -> GridAction.MOVE_NORTH;
        };
    }

    /**
     * Comprueba si una celda (x, y) pertenece a la recta discreta 4-conexa entre start y goal (m-line).
     *
     * En una cuadrícula discreta:
     * |(x - x_s)*dy - (y - y_s)*dx| <= (|dx| + |dy|) / 2
     */
    public boolean isOnMLine(GridPosition pos) {
        if (pos == null) {
            return false;
        }
        if (pos.equals(start) || pos.equals(goal)) {
            return true;
        }

        int x = pos.getX(), y = pos.getY();
        int xs = start.getX(), ys = start.getY();
        int xg = goal.getX(), yg = goal.getY();

        if (x < Math.min(xs, xg) - 1 || x > Math.max(xs, xg) + 1 ||
            y < Math.min(ys, yg) - 1 || y > Math.max(ys, yg) + 1) {
            return false;
        }

        int dx = xg - xs;
        int dy = yg - ys;

        if (dx == 0) return x == xs;
        if (dy == 0) return y == ys;

        long cross = Math.abs((long) (x - xs) * dy - (long) (y - ys) * dx);
        return cross <= (Math.abs(dx) + Math.abs(dy)) / 2L;
    }
}
