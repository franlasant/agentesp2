package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.grid.navigation.Bug2Navigator;
import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * ============================================================================
 * BATERÍA DE PRUEBAS UNITARIAS OFFLINE: Bug2Navigator (Tema 2)
 * ============================================================================
 *
 * Evalúa el comportamiento del algoritmo Bug2 (Seguimiento de Muros con m-line)
 * en un simulador determinista 2D sin conexión a Internet:
 *
 * 1. Campo abierto (10x10): Trayectoria directa sobre la m-line (18 pasos exactos).
 * 2. Muro vertical (10x10): Detección de H_i, contorneo y salida en L_i sobre m-line.
 * 3. Muro horizontal (10x10): Contorneo y salida.
 * 4. Muro diagonal (10x10): Salida del obstáculo en escalera.
 * 5. Barrera en U (10x10): Entrada en concavidad, escape y salida con progreso.
 * 6. Barrera en C (10x10): Escape de cavidad lateral.
 * 7. Meta Encerrada: Parada controlada (detección de ciclo completo sin colisiones).
 * 8. Propiedades geométricas de la m-line.
 */
public class Bug2NavigatorTest {

    public static void main(String[] args) {
        Bug2NavigatorTest test = new Bug2NavigatorTest();
        test.testMLineGeometry();
        test.testOpenField();
        test.testVerticalWall();
        test.testHorizontalWall();
        test.testDiagonalWall();
        test.testUBarrier();
        test.testCBarrier();
        test.testEnclosedGoalControlledStop();
        System.out.println("✅ [Bug2NavigatorTest] Todos los tests de Bug2 superados con éxito.");
    }

    public void testMLineGeometry() {
        GridPosition start = new GridPosition(0, 0);
        GridPosition goal = new GridPosition(10, 10);
        Bug2Navigator nav = new Bug2Navigator(start, goal);

        assertTrue(nav.isOnMLine(start), "Start debe estar en la m-line");
        assertTrue(nav.isOnMLine(goal), "Goal debe estar en la m-line");
        assertTrue(nav.isOnMLine(new GridPosition(5, 5)), "(5,5) debe estar en la m-line diagonal");
        assertFalse(nav.isOnMLine(new GridPosition(0, 10)), "(0,10) no debe pertenecer a la m-line (0,0)->(10,10)");
    }

    public void testOpenField() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        GridPosition start = new GridPosition(0, 0);
        GridPosition goal = new GridPosition(9, 9);

        SimulationResult res = runSimulation(world, start, goal, 50);
        assertTrue(res.reachedGoal, "Debe alcanzar la meta en campo abierto");
        assertEquals(18, res.steps, "El campo abierto debe resolverse en 18 pasos exactos (distancia Manhattan)");
    }

    public void testVerticalWall() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        for (int y = 2; y <= 7; y++) {
            world.addObstacle(new GridPosition(5, y));
        }

        GridPosition start = new GridPosition(1, 5);
        GridPosition goal = new GridPosition(8, 5);

        SimulationResult res = runSimulation(world, start, goal, 60);
        assertTrue(res.reachedGoal, "Debe superar el muro vertical contorneándolo");
        assertTrue(res.steps > 0 && res.steps < 60, "Debe resolverlo dentro del presupuesto de energía");
    }

    public void testHorizontalWall() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        for (int x = 2; x <= 7; x++) {
            world.addObstacle(new GridPosition(x, 5));
        }

        GridPosition start = new GridPosition(5, 1);
        GridPosition goal = new GridPosition(5, 8);

        SimulationResult res = runSimulation(world, start, goal, 60);
        assertTrue(res.reachedGoal, "Debe superar el muro horizontal contorneándolo");
        assertTrue(res.steps > 0 && res.steps < 60, "Debe resolverlo dentro del presupuesto de energía");
    }

    public void testDiagonalWall() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        world.addObstacle(new GridPosition(2, 3));
        world.addObstacle(new GridPosition(3, 4));
        world.addObstacle(new GridPosition(4, 5));
        world.addObstacle(new GridPosition(5, 6));
        world.addObstacle(new GridPosition(6, 7));

        GridPosition start = new GridPosition(1, 1);
        GridPosition goal = new GridPosition(8, 8);

        SimulationResult res = runSimulation(world, start, goal, 80);
        assertTrue(res.reachedGoal, "Debe superar el muro diagonal");
    }

    public void testUBarrier() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        for (int x = 3; x <= 7; x++) world.addObstacle(new GridPosition(x, 6));
        for (int y = 3; y <= 6; y++) world.addObstacle(new GridPosition(3, y));
        for (int y = 3; y <= 6; y++) world.addObstacle(new GridPosition(7, y));

        GridPosition start = new GridPosition(5, 4);
        GridPosition goal = new GridPosition(5, 9);

        SimulationResult res = runSimulation(world, start, goal, 100);
        assertTrue(res.reachedGoal, "Debe escapar de la trampa en U y alcanzar la meta");
    }

    public void testCBarrier() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        for (int y = 3; y <= 7; y++) world.addObstacle(new GridPosition(5, y));
        world.addObstacle(new GridPosition(4, 3));
        world.addObstacle(new GridPosition(4, 7));

        GridPosition start = new GridPosition(1, 5);
        GridPosition goal = new GridPosition(8, 5);

        SimulationResult res = runSimulation(world, start, goal, 80);
        assertTrue(res.reachedGoal, "Debe escapar de la concavidad en C y alcanzar la meta");
    }

    public void testEnclosedGoalControlledStop() {
        SimulatedWorld world = new SimulatedWorld(10, 10);
        GridPosition goal = new GridPosition(5, 5);
        world.addObstacle(new GridPosition(4, 5));
        world.addObstacle(new GridPosition(6, 5));
        world.addObstacle(new GridPosition(5, 4));
        world.addObstacle(new GridPosition(5, 6));

        GridPosition start = new GridPosition(1, 1);

        SimulationResult res = runSimulation(world, start, goal, 150);
        assertFalse(res.reachedGoal, "La meta es inalcanzable");
        assertTrue(res.controlledStop, "Debe realizar una parada controlada al detectar que no hay salida");
    }

    // =========================================================================
    // INFRAESTRUCTURA DEL SIMULADOR OFFLINE
    // =========================================================================

    private static class SimulatedWorld {
        final int width;
        final int height;
        final Set<GridPosition> obstacles = new HashSet<>();

        SimulatedWorld(int width, int height) {
            this.width = width;
            this.height = height;
        }

        void addObstacle(GridPosition pos) {
            obstacles.add(pos);
        }

        boolean isBlocked(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) {
                return true;
            }
            return obstacles.contains(new GridPosition(x, y));
        }

        GridBumpers getBumpers(GridPosition pos) {
            boolean n = isBlocked(pos.getX(), pos.getY() - 1);
            boolean e = isBlocked(pos.getX() + 1, pos.getY());
            boolean s = isBlocked(pos.getX(), pos.getY() + 1);
            boolean w = isBlocked(pos.getX() - 1, pos.getY());
            return new GridBumpers(n, e, s, w);
        }
    }

    private static class SimulationResult {
        final boolean reachedGoal;
        final boolean controlledStop;
        final int steps;

        SimulationResult(boolean reachedGoal, boolean controlledStop, int steps) {
            this.reachedGoal = reachedGoal;
            this.controlledStop = controlledStop;
            this.steps = steps;
        }
    }

    private static SimulationResult runSimulation(SimulatedWorld world, GridPosition start,
                                                   GridPosition goal, int maxSteps) {
        Bug2Navigator navigator = new Bug2Navigator(start, goal);
        GridPosition current = start;
        int steps = 0;

        while (steps < maxSteps) {
            if (current.equals(goal)) {
                return new SimulationResult(true, false, steps);
            }

            GridBumpers bumpers = world.getBumpers(current);
            Optional<GridAction> actionOpt = navigator.nextAction(current, bumpers);

            if (actionOpt.isEmpty()) {
                return new SimulationResult(false, true, steps);
            }

            GridAction action = actionOpt.get();
            if (bumpers.isBlocked(action)) {
                throw new AssertionError("¡Colisión detectada! El agente intentó moverse en dirección " + action
                        + " estando bloqueada en " + current);
            }

            current = current.neighbor(action);
            steps++;
        }

        return new SimulationResult(current.equals(goal), false, steps);
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError("Fallo en test: " + msg);
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) throw new AssertionError("Fallo en test: " + msg);
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " -> Esperado: " + expected + ", Actual: " + actual);
        }
    }
}
