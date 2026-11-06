package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridPosition;
import org.json.JSONObject;

/**
 * Tests unitarios offline para GridPosition, GridAction y GridBumpers.
 * Compatible con ejecución directa por Maven Surefire (POJO test) y por main().
 */
public class GridPositionTest {

    public static void main(String[] args) {
        GridPositionTest test = new GridPositionTest();
        test.testCoordinatesAndNeighbors();
        test.testManhattanDistance();
        test.testActionTowardsAndOpposite();
        test.testFromJsonAndBumpersSafety();
        System.out.println("✅ [GridPositionTest] Todos los tests geométricos y de modelo superados con éxito.");
    }

    public void testCoordinatesAndNeighbors() {
        GridPosition origin = new GridPosition(4, 5);

        // Regla oficial DBA Lab: Norte = y-1, Sur = y+1, Este = x+1, Oeste = x-1
        GridPosition north = origin.neighbor(GridAction.MOVE_NORTH);
        GridPosition south = origin.neighbor(GridAction.MOVE_SOUTH);
        GridPosition east = origin.neighbor(GridAction.MOVE_EAST);
        GridPosition west = origin.neighbor(GridAction.MOVE_WEST);

        assertEquals(new GridPosition(4, 4), north, "Norte debe ser (x, y-1)");
        assertEquals(new GridPosition(4, 6), south, "Sur debe ser (x, y+1)");
        assertEquals(new GridPosition(5, 5), east, "Este debe ser (x+1, y)");
        assertEquals(new GridPosition(3, 5), west, "Oeste debe ser (x-1, y)");
    }

    public void testManhattanDistance() {
        GridPosition a = new GridPosition(1, 2);
        GridPosition b = new GridPosition(7, 9);

        assertEquals(0, a.manhattanDistance(a), "Distancia a sí mismo debe ser 0");
        assertEquals(13, a.manhattanDistance(b), "Distancia Manhattan entre (1,2) y (7,9) debe ser |1-7|+|2-9| = 13");
        assertEquals(13, b.manhattanDistance(a), "Distancia Manhattan debe ser simétrica");
    }

    public void testActionTowardsAndOpposite() {
        GridPosition center = new GridPosition(3, 3);
        for (GridAction action : GridAction.values()) {
            GridPosition next = center.neighbor(action);
            assertEquals(action, center.actionTowards(next), "actionTowards debe identificar la dirección correcta");
            assertEquals(center, next.neighbor(action.opposite()), "Moverse y aplicar opposite() debe volver al origen");
        }
    }

    public void testFromJsonAndBumpersSafety() {
        JSONObject posPayload = new JSONObject()
                .put("sensorId", "grid.position")
                .put("value", new JSONObject().put("x", 8).put("y", 2));
        GridPosition pos = GridPosition.fromJson(posPayload);
        assertEquals(new GridPosition(8, 2), pos, "Parseo JSON de grid.position");

        JSONObject bumpersPayload = new JSONObject()
                .put("sensorId", "grid.bumpers")
                .put("value", new JSONObject()
                        .put("north", true)
                        .put("east", false)
                        .put("south", false)
                        .put("west", true));
        GridBumpers bumpers = GridBumpers.fromJson(bumpersPayload);

        assertTrue(!bumpers.isSafe(GridAction.MOVE_NORTH), "Norte debe estar bloqueado");
        assertTrue(bumpers.isSafe(GridAction.MOVE_EAST), "Este debe ser seguro");
        assertTrue(bumpers.isSafe(GridAction.MOVE_SOUTH), "Sur debe ser seguro");
        assertTrue(!bumpers.isSafe(GridAction.MOVE_WEST), "Oeste debe estar bloqueado");
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " -> Esperado: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Fallo de aserción: " + msg);
        }
    }
}
