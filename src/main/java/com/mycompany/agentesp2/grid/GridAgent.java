package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.api.DbaClient;
import com.mycompany.agentesp2.api.DbaException;
import com.mycompany.agentesp2.grid.navigation.MentalMap;
import com.mycompany.agentesp2.grid.navigation.PathPlanner;
import com.mycompany.agentesp2.model.RunControl;
import com.mycompany.agentesp2.model.RunStatus;
import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridPosition;
import com.mycompany.agentesp2.model.grid.GridState;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;

/**
 * Agente JADE de navegación 2D para el bloque Grid de la Práctica 2 (DBA Lab).
 *
 * Implementa el ciclo Percepción -> Actualización de Mapa Mental -> Planificación A* Optimista
 * -> Validación de Seguridad con Bumpers -> Acción -> Verificación de Estado Terminal.
 */
public class GridAgent extends Agent {

    private DbaClient client;
    private GridState state;
    private MentalMap mentalMap;
    private PathPlanner planner;
    private String challengeName = "GRID_2D";

    @Override
    protected void setup() {
        System.out.println("[GridAgent] Iniciando agente de navegación 2D: " + getLocalName());

        try {
            this.client = DbaClient.fromEnvOrConfig();

            JSONObject challenge = resolveChallenge();
            if (challenge == null) {
                System.err.println("[GridAgent] No se encontró ningún reto disponible de tipo GRID_2D.");
                doDelete();
                return;
            }

            long challengeId = challenge.getLong("id");
            this.challengeName = challenge.optString("name",
                    challenge.optString("code", "Reto GRID #" + challengeId));

            int gridIndex = 1;
            List<JSONObject> allGrid = client.findGridChallenges();
            for (int i = 0; i < allGrid.size(); i++) {
                if (allGrid.get(i).getLong("id") == challengeId) {
                    gridIndex = i + 1;
                    break;
                }
            }

            System.out.printf("[GridAgent] Seleccionado Reto Grid #%d [ID=%d] (%s)%n",
                    gridIndex, challengeId, challengeName);

            RunControl initialControl = client.startRunTyped(challengeId);
            if (initialControl.isTerminal()) {
                System.err.println("[GridAgent] El run se inició ya en estado terminal: " + initialControl);
                doDelete();
                return;
            }

            this.state = client.initGridState(initialControl.getRunId());
            if (state.getControl().isTerminal()) {
                System.out.println("[GridAgent] El run terminó durante la lectura inicial: " + state.getControl());
                doDelete();
                return;
            }

            this.mentalMap = new MentalMap();
            this.mentalMap.registerTarget(state.getTargetPosition());
            this.planner = new PathPlanner();

            System.out.printf("[GridAgent] Run %d ACTIVO | Inicio: %s | Meta: %s | Energía inicial: %s%n",
                    state.getControl().getRunId(),
                    state.getCurrentPosition(),
                    state.getTargetPosition(),
                    state.getEnergy());

            addBehaviour(new NavegacionBehaviour());

        } catch (Exception e) {
            System.err.println("[GridAgent] Error fatal en setup(): " + e.getMessage());
            doDelete();
        }
    }

    private JSONObject resolveChallenge() throws DbaException {
        Object[] args = getArguments();
        String selector = null;
        if (args != null && args.length > 0 && args[0] != null) {
            selector = args[0].toString().trim();
        }
        if (selector == null || selector.isEmpty()) {
            selector = System.getenv("DBA_CHALLENGE_ID");
        }
        if (selector == null || selector.isEmpty()) {
            selector = System.getenv("GRID_CHALLENGE");
        }
        return client.resolveGridChallenge(selector);
    }

    @Override
    protected void takeDown() {
        System.out.println("[GridAgent] Finalizando agente " + getLocalName());
        if (state != null && state.getControl() != null && state.getControl().isActive() && client != null) {
            try {
                long runId = state.getControl().getRunId();
                System.out.println("[GridAgent] Cerrando run activo " + runId + " en DBA Lab...");
                client.closeRun(runId);
            } catch (DbaException e) {
                System.err.println("[GridAgent] Aviso al cerrar el run en takeDown(): " + e.getMessage());
            }
        }
        boolean isPeripheral = "true".equalsIgnoreCase(System.getProperty("JADE_PERIPHERAL_CONTAINER"));
        boolean isHeadless = "false".equalsIgnoreCase(System.getenv("JADE_GUI"));
        if (isPeripheral || isHeadless) {
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                    getContainerController().kill();
                } catch (Exception ignored) {
                }
                System.exit(0);
            }, "jade-container-shutdown").start();
        }
    }

    /**
     * Comportamiento de navegación paso a paso en JADE.
     * Cada llamada a action() ejecuta una iteración del ciclo sin bloquear indefinidamente el hilo del agente.
     */
    private class NavegacionBehaviour extends Behaviour {

        private int step = 0;
        private boolean finished = false;

        @Override
        public void action() {
            if (state == null || state.getControl().isTerminal()) {
                finishNavigation();
                return;
            }

            try {
                step++;
                long runId = state.getControl().getRunId();

                // 1. ACTUALIZAR MAPA MENTAL CON LA PERCEPCIÓN ACTUAL
                mentalMap.updateFromPerceptions(state.getCurrentPosition(), state.getBumpers());

                // 2. MOSTRAR ESTADO, ENERGÍA Y MAPA ASCII
                System.out.println("\n------------------------------------------------------------");
                System.out.printf("[Paso %d | %s] %s%n", step, challengeName, state);
                System.out.println(mentalMap.renderAscii(state.getCurrentPosition(), state.getTargetPosition()));

                if (state.isAtTarget() || state.getControl().isTerminal()) {
                    finishNavigation();
                    return;
                }

                if (!state.getEnergy().hasEnergy()) {
                    System.err.println("[GridAgent] Energía agotada antes de alcanzar la meta.");
                    finishNavigation();
                    return;
                }

                // 3. CALCULAR SIGUIENTE PASO CON A* OPTIMISTA (con reintento local si el bumper contradice)
                GridAction nextAction = selectValidatedSafeAction();
                if (nextAction == null) {
                    System.err.println("[GridAgent] No existe ruta transitable hacia la meta "
                            + state.getTargetPosition() + ". Parada controlada para evitar colisión.");
                    finishNavigation();
                    return;
                }

                // 4. ENVIAR ACCIÓN AL SERVIDOR
                GridPosition expectedNextPos = state.getCurrentPosition().neighbor(nextAction);
                RunControl actionControl = client.sendGridAction(runId, nextAction);
                state.updateControl(actionControl);

                System.out.printf("[Paso %d] Acción enviada: %s -> %s | Estado Acción: %s | Run: %s (Razón: %s)%n",
                        step, nextAction, expectedNextPos,
                        actionControl.getActionStatus(),
                        actionControl.getRunStatus(),
                        actionControl.getReasonCode());

                // 5. COMPROBAR SI EL RUN HA TERMINADO TRAS LA ACCIÓN
                if (state.getControl().isTerminal()) {
                    if (state.getControl().getRunStatus() == RunStatus.SUCCEEDED) {
                        mentalMap.updateFromPerceptions(expectedNextPos, null);
                        System.out.println("\nMapa final al alcanzar la meta:");
                        System.out.println(mentalMap.renderAscii(expectedNextPos, state.getTargetPosition()));
                    }
                    finishNavigation();
                    return;
                }

                // 6. LEER NUEVAS PERCEPCIONES (POSICIÓN, BUMPERS Y ENERGÍA) PARA EL SIGUIENTE TURNO
                client.refreshGridState(state);
                if (state.getControl().isTerminal()) {
                    finishNavigation();
                }

            } catch (Exception e) {
                System.err.println("[GridAgent] Error durante el ciclo de navegación: " + e.getMessage());
                finishNavigation();
            }
        }

        /**
         * Planifica con A* y valida estrictamente con bumpers.isSafe() antes de devolver la acción.
         * Si la acción propuesta estuviera bloqueada por un bumper, marca la celda como OBSTACLE
         * en el MentalMap y replanifica en memoria sin enviar nada al servidor.
         */
        private GridAction selectValidatedSafeAction() {
            for (int attempt = 0; attempt < 4; attempt++) {
                Optional<GridAction> plannedOpt = planner.findNextAction(
                        state.getCurrentPosition(),
                        state.getTargetPosition(),
                        mentalMap
                );
                if (plannedOpt.isEmpty()) {
                    return null;
                }

                GridAction candidate = plannedOpt.get();
                if (state.getBumpers().isSafe(candidate)) {
                    return candidate;
                }

                // Si el bumper indica bloqueo, marcamos el obstáculo y replanificamos sin enviar
                GridPosition blockedCell = state.getCurrentPosition().neighbor(candidate);
                System.out.println("[GridAgent] Prevención de choque: dirección " + candidate
                        + " bloqueada por bumper. Marcando " + blockedCell + " como OBSTACLE y replanificando...");
                mentalMap.markObstacle(blockedCell);
            }
            return null;
        }

        private void finishNavigation() {
            if (finished) {
                return;
            }
            finished = true;
            if (state != null && state.getControl() != null) {
                RunControl ctrl = state.getControl();
                System.out.println("============================================================");
                System.out.printf("  FIN DEL RUN %d [%s]: %s (Razón: %s)%n",
                        ctrl.getRunId(), challengeName, ctrl.getRunStatus(), ctrl.getReasonCode());
                System.out.printf("  Pasos ejecutados: %d | Última energía leída: %s%n",
                        step, state.getEnergy());
                System.out.println("============================================================");
            }
            doDelete();
        }

        @Override
        public boolean done() {
            return finished || state == null || state.getControl().isTerminal();
        }
    }
}
