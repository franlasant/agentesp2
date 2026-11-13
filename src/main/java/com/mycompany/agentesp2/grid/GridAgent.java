package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.api.DbaClient;
import com.mycompany.agentesp2.api.DbaException;
import com.mycompany.agentesp2.grid.navigation.Bug2Navigator;
import com.mycompany.agentesp2.grid.navigation.MentalMap;
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
 * ============================================================================
 * AGENTE COGNITIVO DE NAVEGACIÓN 2D (GridAgent - JADE)
 * ============================================================================
 *
 * Implementa el ciclo formal de percepción y acción para agentes con estado interno
 * presentado en el Tema 2 (Diapositivas 33 y 34):
 *
 *   1. FASE DE PERCEPCIÓN (see):
 *      Recibe las lecturas sensoriales locales desde DBA Lab (posición, bumpers, energía).
 *
 *   2. FASE DE ACTUALIZACIÓN DEL ESTADO COGNITIVO (next):
 *      Integra las observaciones en el MentalMap, descubriendo obstáculos y celdas libres.
 *
 *   3. FASE DE DELIBERACIÓN Y ACCIÓN (action):
 *      El Bug2Navigator decide el siguiente paso mediante el algoritmo Bug2 (Tema 2).
 *      Alterna entre MOTION_TO_GOAL y WALL_FOLLOWING, validando con bumpers.
 */
public class GridAgent extends Agent {

    private DbaClient client;
    private GridState state;
    private MentalMap mentalMap;
    private Bug2Navigator navigator;
    private String challengeName = "GRID_2D";

    @Override
    protected void setup() {
        System.out.println("[GridAgent] Iniciando agente de navegación 2D: " + getLocalName());

        try {
            this.client = DbaClient.fromEnvOrConfig();

            // 1. Identificar y resolver el reto seleccionado
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

            // 2. Iniciar la ejecución (run) en el servidor DBA Lab
            RunControl initialControl = client.startRunTyped(challengeId);
            if (initialControl.isTerminal()) {
                System.err.println("[GridAgent] El run se inició ya en estado terminal: " + initialControl);
                doDelete();
                return;
            }

            // 3. Obtener el estado inicial consolidado
            this.state = client.initGridState(initialControl.getRunId());
            if (state.getControl().isTerminal()) {
                System.out.println("[GridAgent] El run terminó durante la lectura inicial: " + state.getControl());
                doDelete();
                return;
            }

            // 4. Inicializar memoria espacial y navegador Bug2 (Tema 2)
            this.mentalMap = new MentalMap();
            this.mentalMap.registerTarget(state.getTargetPosition());
            this.navigator = new Bug2Navigator(state.getCurrentPosition(), state.getTargetPosition());

            System.out.printf("[GridAgent] Run %d ACTIVO | Inicio: %s | Meta: %s | Energía inicial: %s%n",
                    state.getControl().getRunId(),
                    state.getCurrentPosition(),
                    state.getTargetPosition(),
                    state.getEnergy());

            // 5. Iniciar el comportamiento de navegación en JADE
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
     * =========================================================================
     * COMPORTAMIENTO JADE: Ciclo Percepción-Decisión-Acción Paso a Paso
     * =========================================================================
     */
    private class NavegacionBehaviour extends Behaviour {

        private int step = 0;
        private boolean finished = false;

        @Override
        public void action() {
            if (isFinishedOrTerminal()) {
                finishNavigation();
                return;
            }

            try {
                step++;

                // -------------------------------------------------------------
                // 1. FASE NEXT: Actualización cognitiva y renderizado del mapa
                // -------------------------------------------------------------
                updateCognitiveState();

                // 2. Comprobar si ya alcanzamos la meta o se agotó la batería
                if (checkTerminationConditions()) {
                    return;
                }

                // -------------------------------------------------------------
                // 3. FASE ACTION: Deliberación A* y ejecución del movimiento
                // -------------------------------------------------------------
                if (!deliberateAndExecuteMove()) {
                    return;
                }

                // -------------------------------------------------------------
                // 4. FASE SEE: Percepción del entorno para el siguiente turno
                // -------------------------------------------------------------
                perceiveNextState();

            } catch (Exception e) {
                System.err.println("[GridAgent] Error durante el ciclo de navegación: " + e.getMessage());
                finishNavigation();
            }
        }

        private boolean isFinishedOrTerminal() {
            return finished || state == null || state.getControl() == null || state.getControl().isTerminal();
        }

        /**
         * FASE NEXT: Incorpora las lecturas de los bumpers en el MentalMap
         * y muestra la telemetría y el mapa ASCII en consola.
         */
        private void updateCognitiveState() {
            mentalMap.updateFromPerceptions(state.getCurrentPosition(), state.getBumpers());
            System.out.println("\n------------------------------------------------------------");
            System.out.printf("[Paso %d | %s] %s%n", step, challengeName, state);
            System.out.println(mentalMap.renderAscii(state.getCurrentPosition(), state.getTargetPosition()));
        }

        /**
         * Evalúa las condiciones de parada antes de deliberar:
         * - Meta alcanzada o run concluido.
         * - Batería agotada (guardián de energía).
         */
        private boolean checkTerminationConditions() {
            if (state.isAtTarget() || state.getControl().isTerminal()) {
                finishNavigation();
                return true;
            }
            if (!state.getEnergy().hasEnergy()) {
                System.err.println("[GridAgent] Energía agotada antes de alcanzar la meta.");
                finishNavigation();
                return true;
            }
            return false;
        }

        /**
         * FASE ACTION: Selecciona una acción validada por A*, la envía al servidor DBA Lab
         * y actualiza el estado de control tras el movimiento.
         */
        private boolean deliberateAndExecuteMove() throws DbaException {
            long runId = state.getControl().getRunId();
            GridAction nextAction = selectValidatedSafeAction();

            if (nextAction == null) {
                System.err.println("[GridAgent] No existe ruta transitable hacia la meta "
                        + state.getTargetPosition() + ". Parada controlada para evitar colisión.");
                finishNavigation();
                return false;
            }

            GridPosition expectedNextPos = state.getCurrentPosition().neighbor(nextAction);
            RunControl actionControl = client.sendGridAction(runId, nextAction);
            state.updateControl(actionControl);

            System.out.printf("[Paso %d] Acción enviada: %s -> %s | Estado: %s | Run: %s (Razón: %s)%n",
                    step, nextAction, expectedNextPos,
                    actionControl.getActionStatus(),
                    actionControl.getRunStatus(),
                    actionControl.getReasonCode());

            if (state.getControl().isTerminal()) {
                if (state.getControl().getRunStatus() == RunStatus.SUCCEEDED) {
                    mentalMap.updateFromPerceptions(expectedNextPos, null);
                    System.out.println("\nMapa final al alcanzar la meta:");
                    System.out.println(mentalMap.renderAscii(expectedNextPos, state.getTargetPosition()));
                }
                finishNavigation();
                return false;
            }
            return true;
        }

        /**
         * FASE SEE: Consulta los sensores dinámicos del servidor tras el paso físico
         * para disponer de la percepción inicial del siguiente turno.
         */
        private void perceiveNextState() throws DbaException {
            client.refreshGridState(state);
            if (state.getControl().isTerminal()) {
                finishNavigation();
            }
        }

        /**
         * Decide la acción mediante Bug2 y valida con bumpers antes de enviar la acción.
         */
        private GridAction selectValidatedSafeAction() {
            Optional<GridAction> plannedOpt = navigator.nextAction(
                    state.getCurrentPosition(),
                    state.getBumpers()
            );
            if (plannedOpt.isEmpty()) {
                return null;
            }

            GridAction candidate = plannedOpt.get();
            if (state.getBumpers().isSafe(candidate)) {
                return candidate;
            }

            System.out.println("[GridAgent] Prevención de choque: acción " + candidate
                    + " contradicha por bumpers.");
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
            return finished || (state != null && state.getControl().isTerminal());
        }
    }
}
