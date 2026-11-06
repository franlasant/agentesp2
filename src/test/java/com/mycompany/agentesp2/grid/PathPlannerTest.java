package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.grid.navigation.MentalMap;
import com.mycompany.agentesp2.grid.navigation.PathPlanner;
import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tests unitarios offline del algoritmo Optimistic Online A* (PathPlanner + MentalMap).
 *
 * Simula en memoria un entorno 10x10 con percepción parcial idéntico a DBA Lab:
 * el agente NO conoce el mapa al arrancar; en cada paso solo lee los 4 bumpers locales
 * de su casilla actual, actualiza su MentalMap y decide el siguiente movimiento.
 *
 * Cubre los 7 mapas de la Tanda 1:
 * 1. Campo abierto
 * 2. Muro vertical
 * 3. Muro horizontal
 * 4. Muro diagonal
 * 5. Barrera en U
 * 6. Barrera en C
 * 7. Barrera en arco
 * + Caso de fallo controlado con meta encerrada.
 */
public class PathPlannerTest {

    public static void main(String[] args) {
        PathPlannerTest test = new PathPlannerTest();
        test.testOpenField10x10();
        test.testVerticalWall10x10();
        test.testHorizontalWall10x10();
        test.testDiagonalWall10x10();
        test.testUBarrier10x10();
        test.testCBarrier10x10();
        test.testArchBarrier10x10();
        test.testUnreachableTargetControlledFailure();
        System.out.println("✅ [PathPlannerTest] Todos los escenarios de navegación 10x10 superados sin colisiones.");
    }

    public void testOpenField10x10() {
        String[] map = {
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Campo abierto 10x10", map,
                new GridPosition(0, 0), new GridPosition(9, 9), 50);
        assertTrue(res.reachedTarget, "Debe alcanzar la meta en Campo abierto");
        assertTrue(res.steps == 18, "En campo abierto debe tomar el camino Manhattan óptimo (18 pasos)");
    }

    public void testVerticalWall10x10() {
        String[] map = {
            "..........",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Muro vertical 10x10", map,
                new GridPosition(1, 5), new GridPosition(8, 5), 60);
        assertTrue(res.reachedTarget, "Debe rodear el muro vertical y llegar a la meta");
    }

    public void testHorizontalWall10x10() {
        String[] map = {
            "..........",
            "..........",
            "..........",
            "..........",
            ".########.",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Muro horizontal 10x10", map,
                new GridPosition(5, 1), new GridPosition(5, 8), 60);
        assertTrue(res.reachedTarget, "Debe rodear el muro horizontal y llegar a la meta");
    }

    public void testDiagonalWall10x10() {
        String[] map = {
            "..........",
            ".......#..",
            "......#...",
            ".....#....",
            "....#.....",
            "...#......",
            "..#.......",
            ".#........",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Muro diagonal 10x10", map,
                new GridPosition(1, 1), new GridPosition(8, 8), 80);
        assertTrue(res.reachedTarget, "Debe superar la escalera del muro diagonal sin chocar");
    }

    public void testUBarrier10x10() {
        // Barrera en U abierta hacia arriba (Norte), agente empieza en (4,1) y meta en (4,8)
        String[] map = {
            "..........",
            "..........",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..######..",
            "..........",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Barrera en U 10x10", map,
                new GridPosition(4, 1), new GridPosition(4, 8), 100);
        assertTrue(res.reachedTarget, "Debe salir de la trampa en U sin ciclar y alcanzar la meta");
    }

    public void testCBarrier10x10() {
        // Barrera en C abierta hacia la izquierda (Oeste), agente en (1,4) y meta en (8,4)
        String[] map = {
            "..........",
            "..........",
            "..#####...",
            "......#...",
            "......#...",
            "......#...",
            "......#...",
            "..#####...",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Barrera en C 10x10", map,
                new GridPosition(1, 4), new GridPosition(8, 4), 100);
        assertTrue(res.reachedTarget, "Debe salir de la barrera en C y llegar a la meta");
    }

    public void testArchBarrier10x10() {
        // Barrera en arco envolvente
        String[] map = {
            "..........",
            "..........",
            "...####...",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..........",
            "..........",
            "..........",
            ".........."
        };
        SimulationResult res = runOnlineSimulation("Barrera en arco 10x10", map,
                new GridPosition(4, 5), new GridPosition(4, 0), 100);
        assertTrue(res.reachedTarget, "Debe salir del arco cóncavo hacia el Sur y rodearlo hacia el Norte");
    }

    public void testUnreachableTargetControlledFailure() {
        // Meta en (8,8) totalmente encerrada por muros
        String[] map = {
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".......###",
            ".......#.#",
            ".......###"
        };
        SimulationResult res = runOnlineSimulation("Meta encerrada (Inalcanzable)", map,
                new GridPosition(0, 0), new GridPosition(8, 8), 200);
        assertTrue(!res.reachedTarget, "No puede alcanzar una meta encerrada");
        assertTrue(res.controlledStopWithoutPath, "Debe detenerse de forma controlada al detectar que no existe ruta");
    }

    // =========================================================================
    // MOTOR DE SIMULACIÓN LOCAL DE PERCEPCIÓN PARCIAL (SIN RED)
    // =========================================================================

    private static class SimulationResult {
        final boolean reachedTarget;
        final boolean controlledStopWithoutPath;
        final int steps;
        final int remainingEnergy;

        SimulationResult(boolean reachedTarget, boolean controlledStopWithoutPath, int steps, int remainingEnergy) {
            this.reachedTarget = reachedTarget;
            this.controlledStopWithoutPath = controlledStopWithoutPath;
            this.steps = steps;
            this.remainingEnergy = remainingEnergy;
        }
    }

    private SimulationResult runOnlineSimulation(String scenarioName, String[] asciiRows,
                                                 GridPosition start, GridPosition target, int initialEnergy) {
        int height = asciiRows.length;
        int width = asciiRows[0].length();
        Set<GridPosition> obstacles = new HashSet<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (asciiRows[y].charAt(x) == '#') {
                    obstacles.add(new GridPosition(x, y));
                }
            }
        }

        MentalMap mentalMap = new MentalMap();
        mentalMap.registerTarget(target);
        PathPlanner planner = new PathPlanner();

        GridPosition current = start;
        int energy = initialEnergy;
        int steps = 0;

        while (!current.equals(target) && energy > 0) {
            GridBumpers bumpers = readSimulatedBumpers(current, width, height, obstacles);
            mentalMap.updateFromPerceptions(current, bumpers);

            Optional<GridAction> nextOpt = planner.findNextAction(current, target, mentalMap);
            if (nextOpt.isEmpty()) {
                System.out.printf("  [%s] Parada controlada sin ruta tras %d pasos (energía restante: %d)%n",
                        scenarioName, steps, energy);
                return new SimulationResult(false, true, steps, energy);
            }

            GridAction action = nextOpt.get();

            // Validación crítica antes de moverse: nunca debe enviar una acción bloqueada
            if (!bumpers.isSafe(action)) {
                throw new AssertionError("¡COLISIÓN DETECTADA en " + scenarioName + "! El agente intentó "
                        + action + " desde " + current + " con bumpers " + bumpers);
            }

            GridPosition nextPos = current.neighbor(action);
            if (nextPos.getX() < 0 || nextPos.getX() >= width || nextPos.getY() < 0 || nextPos.getY() >= height
                    || obstacles.contains(nextPos)) {
                throw new AssertionError("¡MOVIMIENTO INVÁLIDO en " + scenarioName + " hacia " + nextPos + "!");
            }

            current = nextPos;
            energy--;
            steps++;
        }

        boolean reached = current.equals(target);
        if (reached) {
            GridBumpers finalBumpers = readSimulatedBumpers(current, width, height, obstacles);
            mentalMap.updateFromPerceptions(current, finalBumpers);
        }

        System.out.printf("  [%s] Éxito=%s | Pasos=%d | Energía restante=%d/%d%n",
                scenarioName, reached, steps, energy, initialEnergy);
        return new SimulationResult(reached, false, steps, energy);
    }

    private GridBumpers readSimulatedBumpers(GridPosition pos, int width, int height, Set<GridPosition> obstacles) {
        return new GridBumpers(
                isCellBlocked(pos.neighbor(GridAction.MOVE_NORTH), width, height, obstacles),
                isCellBlocked(pos.neighbor(GridAction.MOVE_EAST), width, height, obstacles),
                isCellBlocked(pos.neighbor(GridAction.MOVE_SOUTH), width, height, obstacles),
                isCellBlocked(pos.neighbor(GridAction.MOVE_WEST), width, height, obstacles)
        );
    }

    private boolean isCellBlocked(GridPosition pos, int width, int height, Set<GridPosition> obstacles) {
        if (pos.getX() < 0 || pos.getX() >= width || pos.getY() < 0 || pos.getY() >= height) {
            return true;
        }
        return obstacles.contains(pos);
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Fallo en PathPlannerTest: " + msg);
        }
    }
}
